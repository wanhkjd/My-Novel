package io.github.xxyopen.novel.crawl.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 文本编码工具：自动识别 TXT 文件编码并解码为字符串
 *
 * <p>识别顺序：BOM 标记 &gt; 严格 UTF-8 试解码 &gt; GB18030（兼容 GBK/GB2312）。</p>
 */
public class EncodingUtil {

    /**
     * 私有构造，工具类不允许实例化
     */
    private EncodingUtil() {
    }

    /**
     * 将字节数组自动识别编码并解码为字符串
     *
     * @param bytes 文件字节内容
     * @return 解码后的文本（空数组返回空串）
     */
    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        // UTF-8 BOM：EF BB BF
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        // UTF-16 LE BOM：FF FE
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        // UTF-16 BE BOM：FE FF
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        // 无 BOM：先按严格模式尝试 UTF-8（非法字节序列会抛异常而不是替换成乱码）
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            // 不是合法 UTF-8，按中文 TXT 最常见的 GB 系编码解码（GB18030 向下兼容 GBK/GB2312）
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

}
