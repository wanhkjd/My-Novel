package novel.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import novel.core.ai.memory.RedisChatMemory.Snapshot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Executes the production Lua scripts against an owned, non-persistent Redis process.
 * Set -Dredis.test.server=/path/to/redis-server (or REDIS_TEST_SERVER) if it is not on PATH.
 * No application context, user Redis configuration, or existing Redis service is used.
 */
@Timeout(30)
class RedisChatMemoryIntegrationTest {

    private static final String PREFIX = "Cache::Novel::";
    private static final long THREE_DAYS_MILLIS = Duration.ofDays(3).toMillis();
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    static Path redisDirectory;

    private static Process redisProcess;
    private static LettuceConnectionFactory firstFactory;
    private static LettuceConnectionFactory secondFactory;
    private static StringRedisTemplate redis;
    private static StringRedisTemplate peerRedis;

    private RedisChatMemory memory;
    private RedisChatMemory peer;
    private String conversation;
    private String key;

    @BeforeAll
    static void startOwnedRedis() throws Exception {
        Path executable = redisExecutable();
        assumeTrue(executable != null,
            "Real Redis regression tests require redis-server; set redis.test.server or REDIS_TEST_SERVER");
        int port;
        do {
            try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
                port = socket.getLocalPort();
            }
        } while (port == 6379);

        Path log = redisDirectory.resolve("redis.log");
        try {
            // ProcessBuilder does not launch a shell or a visible Windows console window.
            redisProcess = new ProcessBuilder(executable.toString(),
                "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--save", "", "--appendonly", "no", "--daemonize", "no",
                "--dir", redisDirectory.toString())
                .directory(redisDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

            // Verify this process owns the listener before connecting. A port collision must
            // fail setup rather than accidentally send commands to somebody else's Redis.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean ready = false;
            while (System.nanoTime() < deadline && redisProcess.isAlive()) {
                if (Files.readString(log, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT)
                    .contains("ready to accept connections")) {
                    ready = true;
                    break;
                }
                Thread.sleep(25);
            }
            assertThat(ready && redisProcess.isAlive())
                .withFailMessage("Owned Redis failed to start on port %s:%n%s", port, Files.readString(log))
                .isTrue();

            firstFactory = newConnectionFactory(port);
            secondFactory = newConnectionFactory(port);
            redis = new StringRedisTemplate(firstFactory);
            peerRedis = new StringRedisTemplate(secondFactory);
            try (var connection = firstFactory.getConnection()) {
                assertThat(connection.ping()).isEqualTo("PONG");
            }
        } catch (Exception | AssertionError failure) {
            stopOwnedRedis();
            throw failure;
        }
    }

    @AfterAll
    static void stopOwnedRedis() throws InterruptedException {
        try {
            if (secondFactory != null) {
                secondFactory.destroy();
                secondFactory = null;
            }
        } finally {
            try {
                if (firstFactory != null) {
                    firstFactory.destroy();
                    firstFactory = null;
                }
            } finally {
                if (redisProcess != null) {
                    redisProcess.destroy();
                    if (!redisProcess.waitFor(5, TimeUnit.SECONDS)) {
                        redisProcess.destroyForcibly();
                        assertThat(redisProcess.waitFor(5, TimeUnit.SECONDS)).isTrue();
                    }
                    redisProcess = null;
                }
            }
        }
    }

    @BeforeEach
    void setUp() {
        memory = new RedisChatMemory(redis);
        peer = new RedisChatMemory(peerRedis);
        conversation = "aiChatMemory::1001::" + UUID.randomUUID();
        key = PREFIX + conversation;
    }

    @Test
    void beginCreatesOneHashWithRandomGenerationEmptyHistoryAndThreeDayTtl() throws Exception {
        Snapshot snapshot = memory.begin(conversation);

        assertThat(snapshot.conversationId()).isEqualTo(conversation);
        assertThat(UUID.fromString(snapshot.generation()).toString()).isEqualTo(snapshot.generation());
        assertThat(snapshot.messages()).isEmpty();
        assertThat(redis.type(key)).isEqualTo(DataType.HASH);
        assertThat(redis.opsForHash().keys(key)).containsExactlyInAnyOrder("generation", "messages");
        assertThat(redis.opsForHash().get(key, "generation")).isEqualTo(snapshot.generation());
        assertThat(JSON.readTree((String) redis.opsForHash().get(key, "messages")).isArray()).isTrue();
        assertThat(redis.keys(key + "*")).containsExactly(key);
        assertThreeDayTtl();
        assertThat(peer.begin(conversation).generation()).isEqualTo(snapshot.generation());
    }

    @ParameterizedTest(name = "clear rejects old snapshot, separate client = {0}")
    @ValueSource(booleans = {false, true})
    void clearRejectsInFlightTurnWithoutRecreatingKey(boolean separateClient) {
        Snapshot old = memory.begin(conversation);
        RedisChatMemory clearer = separateClient ? peer : memory;

        clearer.clear(conversation);

        assertThat(memory.appendTurn(old, "late question", "late answer")).isFalse();
        assertThat(clearer.get(conversation, 20)).isEmpty();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @ParameterizedTest(name = "new generation survives stale append, separate client = {0}")
    @ValueSource(booleans = {false, true})
    void oldTurnCannotOverwriteNewGenerationOrExtendItsTtl(boolean separateClient) {
        Snapshot old = memory.begin(conversation);
        RedisChatMemory replacement = separateClient ? peer : memory;
        replacement.clear(conversation);
        Snapshot current = replacement.begin(conversation);
        assertThat(current.generation()).isNotEqualTo(old.generation());
        assertThat(replacement.appendTurn(current, "new question", "new answer")).isTrue();
        redis.expire(key, Duration.ofSeconds(60));
        Object messagesBefore = redis.opsForHash().get(key, "messages");
        long ttlBefore = ttlMillis();

        assertThat(memory.appendTurn(old, "stale question", "stale answer")).isFalse();

        assertThat(redis.opsForHash().get(key, "messages")).isEqualTo(messagesBefore);
        assertThat(redis.opsForHash().get(key, "generation")).isEqualTo(current.generation());
        assertContents(replacement.get(conversation, 20), "new question", "new answer");
        assertThat(ttlMillis()).isPositive().isLessThanOrEqualTo(ttlBefore);
    }

    @Test
    void appendBeforeClearIsRemovedAndCannotBeRetried() {
        Snapshot snapshot = memory.begin(conversation);
        assertThat(memory.appendTurn(snapshot, "question", "answer")).isTrue();
        assertThat(peer.get(conversation, 20)).hasSize(2);

        peer.clear(conversation);

        assertThat(memory.get(conversation, 20)).isEmpty();
        assertThat(memory.appendTurn(snapshot, "retry", "retry answer")).isFalse();
        assertThat(redis.hasKey(key)).isFalse();
    }

    @ParameterizedTest(name = "old snapshot rejected after expiry = {0}, otherwise external DEL")
    @ValueSource(booleans = {false, true})
    void expiryOrExternalDeleteInvalidatesOldSnapshotPermanently(boolean expire) throws Exception {
        Snapshot old = memory.begin(conversation);
        assertThat(memory.appendTurn(old, "before", "removal")).isTrue();
        if (expire) {
            assertThat(redis.expire(key, Duration.ofMillis(100))).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
        } else {
            assertThat(peerRedis.delete(key)).isTrue();
        }
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(memory.appendTurn(old, "late", "answer")).isFalse();
        assertThat(redis.hasKey(key)).isFalse();

        Snapshot current = peer.begin(conversation);
        assertThat(current.generation()).isNotEqualTo(old.generation());
        assertThat(current.messages()).isEmpty();
        assertThat(peer.appendTurn(current, "fresh", "answer")).isTrue();
        assertThat(memory.appendTurn(old, "still stale", "answer")).isFalse();
        assertContents(memory.get(conversation, 20), "fresh", "answer");
    }

    @Test
    void appendSerializesWholeTurnTrimsToLatestTwentyAndRenewsThreeDayTtl() throws Exception {
        Snapshot snapshot = memory.begin(conversation);
        for (int turn = 0; turn < 15; turn++) {
            assertThat(memory.appendTurn(snapshot, "q-" + turn, "a-" + turn)).isTrue();
        }
        assertContents(memory.get(conversation, Integer.MAX_VALUE), turnContents(5, 15));
        assertContents(peer.begin(conversation).messages(), turnContents(5, 15));
        redis.expire(key, Duration.ofSeconds(30));
        String question = "你好 \"quoted\"\\path\n下一行";
        String answer = "少侠好\n\t答案 😀";

        assertThat(peer.appendTurn(snapshot, question, answer)).isTrue();

        assertThreeDayTtl();
        assertContents(memory.get(conversation, 2), question, answer);
        assertContents(memory.get(conversation, 1), answer);
        List<Message> messages = memory.get(conversation, 20);
        assertThat(messages).hasSize(20);
        assertThat(messages.get(0).getContent()).isEqualTo("q-6");
        assertThat(messages.get(18).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(messages.get(19).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        JsonNode stored = JSON.readTree((String) redis.opsForHash().get(key, "messages"));
        assertThat(stored).hasSize(20);
        for (JsonNode message : stored) {
            assertThat(message.isTextual()).isTrue();
            assertThat(JSON.readTree(message.textValue()).hasNonNull("content")).isTrue();
        }
    }

    @Test
    void independentUsersWithSameConversationSuffixDoNotShareGenerationsHistoryOrClear() {
        String suffix = UUID.randomUUID().toString();
        String alice = "aiChatMemory::1001::" + suffix;
        String bob = "aiChatMemory::1002::" + suffix;
        Snapshot aliceSnapshot = memory.begin(alice);
        Snapshot bobSnapshot = peer.begin(bob);
        assertThat(aliceSnapshot.generation()).isNotEqualTo(bobSnapshot.generation());
        assertThat(memory.appendTurn(aliceSnapshot, "alice", "alice answer")).isTrue();
        assertThat(peer.appendTurn(bobSnapshot, "bob", "bob answer")).isTrue();

        memory.clear(alice);

        assertThat(memory.appendTurn(aliceSnapshot, "stale alice", "answer")).isFalse();
        assertThat(peer.appendTurn(bobSnapshot, "bob again", "answer again")).isTrue();
        assertThat(memory.get(alice, 20)).isEmpty();
        assertContents(memory.get(bob, 20), "bob", "bob answer", "bob again", "answer again");
        assertThat(peer.begin(bob).generation()).isEqualTo(bobSnapshot.generation());
    }

    @Test
    void getMissingConversationAndNonPositiveLimitsNeverCreateAKey() {
        assertThat(memory.get(conversation, 20)).isEmpty();
        assertThat(memory.get(conversation, 0)).isEmpty();
        assertThat(memory.get(conversation, -1)).isEmpty();
        assertThat(memory.get(conversation, Integer.MIN_VALUE)).isEmpty();
        memory.clear(conversation);
        assertThat(redis.hasKey(key)).isFalse();
    }

    @Test
    void getDoesNotExtendExistingHashTtl() {
        Snapshot snapshot = memory.begin(conversation);
        memory.appendTurn(snapshot, "question", "answer");
        redis.expire(key, Duration.ofSeconds(60));
        long before = ttlMillis();

        assertContents(memory.get(conversation, 20), "question", "answer");

        assertThat(ttlMillis()).isPositive().isLessThanOrEqualTo(before);
    }

    @Test
    void legacyReadDoesNotMigrateAndBeginPreservesLatestTwentyAndPositiveTtl() throws Exception {
        List<String> legacy = serializedTurns(15);
        redis.opsForList().rightPushAll(key, legacy);
        redis.expire(key, Duration.ofMillis(90_123));
        long before = ttlMillis();

        assertContents(memory.get(conversation, 3), "a-13", "q-14", "a-14");
        assertContents(memory.get(conversation, Integer.MAX_VALUE), turnContents(5, 15));
        assertThat(redis.type(key)).isEqualTo(DataType.LIST);
        assertThat(redis.opsForList().range(key, 0, -1)).containsExactlyElementsOf(legacy);
        assertThat(ttlMillis()).isPositive().isLessThanOrEqualTo(before);

        Snapshot migrated = memory.begin(conversation);

        assertContents(migrated.messages(), turnContents(5, 15));
        assertThat(redis.type(key)).isEqualTo(DataType.HASH);
        assertThat(ttlMillis()).isBetween(before - 5_000, before);
        assertThat(UUID.fromString(migrated.generation()).toString()).isEqualTo(migrated.generation());
        assertThat(JSON.readTree((String) redis.opsForHash().get(key, "messages"))).hasSize(20);
        assertThat(peer.begin(conversation).generation()).isEqualTo(migrated.generation());
        assertThat(peer.appendTurn(migrated, "migrated q", "migrated a")).isTrue();
        assertThat(memory.get(conversation, 20).get(0).getContent()).isEqualTo("q-6");
        assertThreeDayTtl();
    }

    @Test
    void legacyWithoutExpiryReceivesThreeDayTtlOnlyWhenMigrated() throws Exception {
        redis.opsForList().rightPushAll(key, serializedTurns(1));
        assertThat(ttlMillis()).isEqualTo(-1);
        assertContents(memory.get(conversation, 20), "q-0", "a-0");
        assertThat(ttlMillis()).isEqualTo(-1);

        Snapshot migrated = memory.begin(conversation);

        assertContents(migrated.messages(), "q-0", "a-0");
        assertThreeDayTtl();
    }

    @Test
    void simultaneousLegacyMigrationKeepsOneGenerationAndOriginalTtl() throws Exception {
        redis.opsForList().rightPushAll(key, serializedTurns(12));
        redis.expire(key, Duration.ofSeconds(90));
        long before = ttlMillis();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Snapshot> first = executor.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return memory.begin(conversation);
            });
            Future<Snapshot> second = executor.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return peer.begin(conversation);
            });
            start.countDown();
            Snapshot firstSnapshot = first.get(10, TimeUnit.SECONDS);
            Snapshot secondSnapshot = second.get(10, TimeUnit.SECONDS);
            assertThat(firstSnapshot.generation()).isEqualTo(secondSnapshot.generation());
            assertContents(firstSnapshot.messages(), turnContents(2, 12));
            assertContents(secondSnapshot.messages(), turnContents(2, 12));
            assertThat(ttlMillis()).isBetween(before - 5_000, before);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void corruptLegacyAndHashMessagesAreSkippedWithoutLosingValidOrder() throws Exception {
        List<String> raw = List.of("not-json", "null", "{}",
            JSON.writeValueAsString(new RedisChatMemory.MemoryEntry("user", "有效问题")),
            "{\"role\":\"unknown\",\"content\":\"ignore\"}",
            JSON.writeValueAsString(new RedisChatMemory.MemoryEntry("assistant", "有效回答")), "{");
        redis.opsForList().rightPushAll(key, raw);

        assertContents(memory.get(conversation, 20), "有效问题", "有效回答");
        Snapshot migrated = memory.begin(conversation);
        assertContents(migrated.messages(), "有效问题", "有效回答");
        assertContents(peer.get(conversation, 20), "有效问题", "有效回答");
        assertThat(peer.appendTurn(migrated, "new question", "new answer")).isTrue();
        assertContents(memory.get(conversation, 20), "有效问题", "有效回答", "new question", "new answer");
    }

    @Test
    void snapshotDefensivelyCopiesHistoryAndRemainsUnchangedAfterAppendAndClear() {
        List<Message> mutable = new ArrayList<>(List.of(new UserMessage("original")));
        Snapshot constructed = new Snapshot(conversation, UUID.randomUUID().toString(), mutable);
        mutable.clear();
        assertContents(constructed.messages(), "original");
        assertThatThrownBy(() -> constructed.messages().clear()).isInstanceOf(UnsupportedOperationException.class);

        Snapshot empty = memory.begin(conversation);
        memory.appendTurn(empty, "first q", "first a");
        Snapshot captured = peer.begin(conversation);
        memory.appendTurn(captured, "second q", "second a");
        peer.clear(conversation);

        assertThat(empty.messages()).isEmpty();
        assertContents(captured.messages(), "first q", "first a");
        assertThatThrownBy(() -> captured.messages().add(new UserMessage("mutation")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullTurnInputRejectsWholePairWithoutChangingMessagesOrTtl() {
        Snapshot snapshot = memory.begin(conversation);
        redis.expire(key, Duration.ofSeconds(60));
        Object before = redis.opsForHash().get(key, "messages");
        long ttlBefore = ttlMillis();

        assertThatNullPointerException().isThrownBy(() -> memory.appendTurn(snapshot, null, "answer"));
        assertThatNullPointerException().isThrownBy(() -> memory.appendTurn(snapshot, "question", null));
        assertThatNullPointerException().isThrownBy(() -> memory.appendTurn(null, "question", "answer"));

        assertThat(redis.opsForHash().get(key, "messages")).isEqualTo(before);
        assertThat(memory.get(conversation, 20)).isEmpty();
        assertThat(ttlMillis()).isPositive().isLessThanOrEqualTo(ttlBefore);
    }

    @Test
    void concurrentAppendsDoNotLoseAnyTurnBeforeTheWindowIsFull() throws Exception {
        Snapshot snapshot = memory.begin(conversation);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> writes = new ArrayList<>();
            for (int turn = 0; turn < 10; turn++) {
                int id = turn;
                writes.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    RedisChatMemory client = id % 2 == 0 ? memory : peer;
                    return client.appendTurn(snapshot, "q-" + id, "a-" + id);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Boolean> write : writes) {
                assertThat(write.get(10, TimeUnit.SECONDS)).isTrue();
            }
            List<Message> messages = memory.get(conversation, 20);
            assertThat(messages).extracting(Message::getContent)
                .containsExactlyInAnyOrder(turnContents(0, 10));
            assertCompletePairs(messages);
        } finally {
            start.countDown();
            shutdown(executor);
        }
    }
    @Test
    void concurrentAppendsExposeOnlyCompletePairsWithinTheTwentyMessageWindow() throws Exception {
        Snapshot snapshot = memory.begin(conversation);
        int writers = 6;
        ExecutorService executor = Executors.newFixedThreadPool(writers + 1);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch readerStarted = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        AtomicInteger observations = new AtomicInteger();
        try {
            Future<?> reader = executor.submit(() -> {
                readerStarted.countDown();
                while (!finished.get()) {
                    assertCompletePairs(peer.get(conversation, 20));
                    Snapshot observed = peer.begin(conversation);
                    assertThat(observed.generation()).isEqualTo(snapshot.generation());
                    assertCompletePairs(observed.messages());
                    observations.incrementAndGet();
                }
            });
            assertThat(readerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            List<Future<?>> writes = new ArrayList<>();
            for (int writer = 0; writer < writers; writer++) {
                int writerId = writer;
                writes.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    RedisChatMemory client = writerId % 2 == 0 ? memory : peer;
                    for (int turn = 0; turn < 30; turn++) {
                        String id = writerId + "-" + turn;
                        assertThat(client.appendTurn(snapshot, "q-" + id, "a-" + id)).isTrue();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> write : writes) {
                write.get(15, TimeUnit.SECONDS);
            }
            finished.set(true);
            reader.get(5, TimeUnit.SECONDS);
            assertThat(observations.get()).isPositive();
            List<Message> finalWindow = memory.get(conversation, 20);
            assertThat(finalWindow).hasSize(20);
            assertCompletePairs(finalWindow);
            assertThat(finalWindow.stream().filter(m -> m.getMessageType() == MessageType.USER)
                .map(Message::getContent).toList()).doesNotHaveDuplicates();
            assertThreeDayTtl();
        } finally {
            finished.set(true);
            start.countDown();
            shutdown(executor);
        }
    }

    @Test
    void beginNeverCombinesHistoryFromOneGenerationWithAnotherDuringConcurrentClear() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> resets = executor.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                for (int i = 0; i < 100; i++) {
                    memory.clear(conversation);
                    Snapshot fresh = memory.begin(conversation);
                    assertThat(memory.appendTurn(fresh, "q-" + fresh.generation(), "a-" + fresh.generation()))
                        .isTrue();
                }
                return null;
            });
            Future<?> reads = executor.submit(() -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                for (int i = 0; i < 200; i++) {
                    Snapshot observed = peer.begin(conversation);
                    if (!observed.messages().isEmpty()) {
                        assertContents(observed.messages(), "q-" + observed.generation(), "a-" + observed.generation());
                    }
                }
                return null;
            });
            start.countDown();
            resets.get(15, TimeUnit.SECONDS);
            reads.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            shutdown(executor);
        }
    }

    private static void assertCompletePairs(List<Message> messages) {
        assertThat(messages.size()).isLessThanOrEqualTo(20);
        assertThat(messages.size() % 2).isZero();
        for (int i = 0; i < messages.size(); i += 2) {
            Message question = messages.get(i);
            Message answer = messages.get(i + 1);
            assertThat(question.getMessageType()).isEqualTo(MessageType.USER);
            assertThat(answer.getMessageType()).isEqualTo(MessageType.ASSISTANT);
            assertThat(question.getContent()).startsWith("q-");
            assertThat(answer.getContent()).isEqualTo("a-" + question.getContent().substring(2));
        }
    }

    private static void assertContents(List<Message> messages, String... expected) {
        assertThat(messages).extracting(Message::getContent).containsExactly(expected);
    }

    private static String[] turnContents(int first, int end) {
        List<String> contents = new ArrayList<>();
        for (int turn = first; turn < end; turn++) {
            contents.add("q-" + turn);
            contents.add("a-" + turn);
        }
        return contents.toArray(String[]::new);
    }

    private static List<String> serializedTurns(int count) throws Exception {
        List<String> raw = new ArrayList<>();
        for (int turn = 0; turn < count; turn++) {
            raw.add(JSON.writeValueAsString(new RedisChatMemory.MemoryEntry("user", "q-" + turn)));
            raw.add(JSON.writeValueAsString(new RedisChatMemory.MemoryEntry("assistant", "a-" + turn)));
        }
        return raw;
    }

    private long ttlMillis() {
        return redis.getExpire(key, TimeUnit.MILLISECONDS);
    }

    private void assertThreeDayTtl() {
        assertThat(ttlMillis()).isBetween(THREE_DAYS_MILLIS - 5_000, THREE_DAYS_MILLIS);
    }

    private static void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    private static LettuceConnectionFactory newConnectionFactory(int port) {
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(3)).shutdownTimeout(Duration.ofMillis(100)).build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", port), client);
        factory.afterPropertiesSet();
        return factory;
    }

    private static Path redisExecutable() throws IOException {
        String configured = System.getProperty("redis.test.server");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("REDIS_TEST_SERVER");
        }
        if (configured != null && !configured.isBlank()) {
            Path executable = Path.of(configured).toAbsolutePath();
            if (!Files.isRegularFile(executable)) {
                throw new IOException("Configured Redis executable does not exist: " + executable);
            }
            return executable;
        }
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            Path local = Path.of("D:/javaweb/Redis/redis-server.exe");
            if (Files.isRegularFile(local)) {
                return local;
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String directory : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (!directory.isBlank()) {
                    Path candidate = Path.of(directory, windows ? "redis-server.exe" : "redis-server");
                    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                        return candidate.toAbsolutePath();
                    }
                }
            }
        }
        return null;
    }
}
