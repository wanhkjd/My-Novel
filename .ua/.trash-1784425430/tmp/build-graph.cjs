const fs = require("fs");
const path = require("path");
const cp = require("child_process");

const projectRoot = process.cwd();
const uaDir = path.join(projectRoot, ".ua");
const intermediateDir = path.join(uaDir, "intermediate");
const tmpDir = path.join(uaDir, "tmp");

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function rel(p) {
  return p.replaceAll("\\", "/");
}

function existsRel(filePath) {
  return fs.existsSync(path.join(projectRoot, filePath));
}

function readRel(filePath) {
  return fs.readFileSync(path.join(projectRoot, filePath), "utf8");
}

function nodeIdForFile(file) {
  if (file.fileCategory === "docs") return `document:${file.path}`;
  if (file.fileCategory === "config") return `config:${file.path}`;
  if (file.fileCategory === "data") return `schema:${file.path}`;
  return `file:${file.path}`;
}

function addNode(node) {
  if (!node.id || nodesById.has(node.id)) return;
  if (!node.tags || !node.tags.length) node.tags = ["未分类"];
  if (!node.summary) node.summary = `${node.name} 的项目节点。`;
  nodesById.set(node.id, node);
}

function addEdge(source, target, type, weight = 0.5, label) {
  if (!source || !target || source === target) return;
  const key = `${source}|${target}|${type}`;
  if (edgesByKey.has(key)) return;
  edgesByKey.set(key, { source, target, type, weight, ...(label ? { label } : {}) });
}

function basenameNoExt(filePath) {
  return path.basename(filePath).replace(/\.[^.]+$/, "");
}

function packageLayer(filePath) {
  const normalized = rel(filePath);
  if (normalized === "README.md" || normalized.startsWith("doc/")) return "文档与数据脚本";
  if (normalized === "pom.xml" || normalized.startsWith(".mvn/") || normalized.startsWith("mvnw")) return "构建与项目配置";
  if (normalized.startsWith("src/test/")) return "测试与生成模板";
  if (normalized.startsWith("src/main/resources/mapper/")) return "数据访问层";
  if (normalized.startsWith("src/main/resources/")) return "应用配置";
  if (normalized.includes("/controller/")) return "Web API 层";
  if (normalized.includes("/service/")) return "业务服务层";
  if (normalized.includes("/manager/")) return "业务支撑管理层";
  if (normalized.includes("/dao/")) return "数据访问层";
  if (normalized.includes("/dto/")) return "DTO 与传输模型";
  if (normalized.includes("/core/config/") || normalized.includes("/core/interceptor/") || normalized.includes("/core/filter/") || normalized.includes("/core/aspect/") || normalized.includes("/core/auth/") || normalized.includes("/core/wrapper/") || normalized.includes("/core/listener/")) return "基础设施与横切能力";
  if (normalized.includes("/core/common/") || normalized.includes("/core/constant/") || normalized.includes("/core/util/") || normalized.includes("/core/json/") || normalized.includes("/core/annotation/")) return "核心公共能力";
  if (normalized.includes("/core/task/")) return "定时任务与异步流程";
  if (normalized.endsWith("NovelApplication.java")) return "应用启动";
  return "核心公共能力";
}

function fileSummary(filePath, language, category) {
  const n = rel(filePath);
  const base = basenameNoExt(n);
  if (n.endsWith("NovelApplication.java")) return "Spring Boot 应用入口，负责启动小说平台后端服务。";
  if (n.includes("/controller/front/")) return `${base} 是读者端 API 控制器，承接前台页面和移动端的 HTTP 请求。`;
  if (n.includes("/controller/author/")) return `${base} 是作家端 API 控制器，处理作者创作、作品管理或 AI 辅助相关请求。`;
  if (n.includes("/service/impl/")) return `${base} 实现业务服务接口，编排缓存、数据访问、搜索或消息等支撑能力。`;
  if (n.includes("/service/")) return `${base} 定义业务服务边界，是控制器和具体实现之间的契约。`;
  if (n.includes("/manager/cache/")) return `${base} 封装缓存读写逻辑，减少业务服务对 Redis/缓存细节的直接依赖。`;
  if (n.includes("/manager/redis/")) return `${base} 封装 Redis 相关能力。`;
  if (n.includes("/manager/dao/")) return `${base} 聚合 DAO/Mapper 操作，为服务层提供更贴近业务的持久化访问。`;
  if (n.includes("/manager/mq/")) return `${base} 封装消息队列发送或管理逻辑。`;
  if (n.includes("/manager/message/")) return `${base} 负责站内信、邮件或系统通知的消息发送流程。`;
  if (n.includes("/dao/mapper/")) return `${base} 是 MyBatis-Plus Mapper 接口，提供实体数据访问入口。`;
  if (n.includes("/dao/entity/")) return `${base} 是数据库实体模型，对应业务表结构。`;
  if (n.includes("/dto/req/")) return `${base} 是请求 DTO，用于承载接口入参。`;
  if (n.includes("/dto/resp/")) return `${base} 是响应 DTO，用于组织接口返回数据。`;
  if (n.includes("/dto/es/")) return `${base} 是 Elasticsearch 文档 DTO，用于搜索索引数据建模。`;
  if (n.includes("/core/config/")) return `${base} 提供 Spring/中间件配置，连接框架能力与项目运行参数。`;
  if (n.includes("/core/interceptor/")) return `${base} 是 Web 拦截器，处理认证、限流、文件访问或上下文注入。`;
  if (n.includes("/core/filter/")) return `${base} 是 Servlet Filter，处理请求进入 Spring MVC 前的横切逻辑。`;
  if (n.includes("/core/aspect/")) return `${base} 是 AOP 切面，承载锁、校验等横切行为。`;
  if (n.includes("/core/auth/")) return `${base} 定义认证授权策略或用户上下文。`;
  if (n.includes("/core/task/")) return `${base} 是定时/后台任务，用于同步或维护派生数据。`;
  if (n.includes("/core/common/exception/")) return `${base} 定义统一异常或异常处理逻辑。`;
  if (n.includes("/core/common/resp/")) return `${base} 定义统一响应模型。`;
  if (n.includes("/core/common/req/")) return `${base} 定义通用请求模型。`;
  if (n.includes("/core/common/util/") || n.includes("/core/util/")) return `${base} 是通用工具类，支撑安全、排序、验证码或网络信息处理。`;
  if (n.includes("/core/constant/")) return `${base} 保存系统常量，集中约束路由、缓存、消息或数据库配置名。`;
  if (n.includes("/resources/mapper/")) return `${base} 是 MyBatis XML 映射文件，定义 SQL 语句和结果映射。`;
  if (n.endsWith("application.yml")) return "Spring Boot 主配置，声明数据源、缓存、搜索、消息、接口文档和业务开关。";
  if (n.endsWith("shardingsphere-jdbc.yml")) return "ShardingSphere JDBC 配置，描述分库分表、读写分离或分片规则。";
  if (n.endsWith("logback-spring.xml")) return "Logback 日志配置，控制应用日志格式、级别和输出位置。";
  if (n.endsWith("pom.xml")) return "Maven 构建配置，定义 Spring Boot 后端依赖、插件和项目元数据。";
  if (n.endsWith(".sql")) return `${base} 保存数据库建表或初始化数据脚本。`;
  if (n.endsWith(".md")) return `${base} 是项目文档，描述项目用途、结构或开发规范。`;
  return `${base} 是 ${language || category || "项目"} 文件，参与小说平台后端实现。`;
}

function fileTags(filePath, language, category) {
  const n = rel(filePath);
  const tags = new Set([language || category || "文件"]);
  tags.add(packageLayer(n));
  if (n.includes("/controller/")) tags.add("Spring MVC");
  if (n.includes("/service/")) tags.add("Service");
  if (n.includes("/dao/mapper/") || n.includes("/resources/mapper/")) tags.add("MyBatis");
  if (n.includes("/dao/entity/")) tags.add("Entity");
  if (n.includes("/dto/")) tags.add("DTO");
  if (n.includes("/manager/cache/")) tags.add("Cache");
  if (n.includes("/manager/mq/")) tags.add("RabbitMQ");
  if (n.includes("/core/config/")) tags.add("Configuration");
  if (n.includes("/core/aspect/")) tags.add("AOP");
  if (n.includes("/core/interceptor/") || n.includes("/core/filter/")) tags.add("Web 横切");
  if (n.endsWith(".sql")) tags.add("SQL");
  return [...tags];
}

function parseJava(content) {
  const pkg = (content.match(/^\s*package\s+([\w.]+)\s*;/m) || [])[1] || "";
  const imports = [...content.matchAll(/^\s*import\s+(?:static\s+)?([\w.]+)(?:\.\*)?\s*;/gm)].map(m => m[1]);
  const classMatch = content.match(/\b(?:class|interface|enum|record)\s+(\w+)/);
  const className = classMatch ? classMatch[1] : null;
  const annotations = [...content.matchAll(/@(\w+)(?:\(([^)]*)\))?/g)].map(m => ({ name: m[1], value: m[2] || "" }));
  return { pkg, imports, className, annotations };
}

function annotationValue(raw) {
  if (!raw) return "";
  const firstString = raw.match(/"([^"]+)"/);
  if (firstString) return firstString[1];
  return raw.replace(/^value\s*=\s*/, "").replace(/^path\s*=\s*/, "").replaceAll("\"", "").trim();
}

function normalizeRoute(base, part) {
  const joined = `/${[base, part].filter(Boolean).join("/")}`.replace(/\/+/g, "/");
  return joined === "/" ? "/" : joined.replace(/\/$/, "");
}

function routeFromMethod(method) {
  const match = method.match(/@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)(?:\(([^)]*)\))?/);
  if (!match) return null;
  const http = match[1].replace("Mapping", "").replace("Request", "ANY").toUpperCase();
  return { http: http === "ANY" ? "ANY" : http, path: annotationValue(match[2] || "") };
}

function layerId(name) {
  const map = {
    "文档与数据脚本": "layer:docs-and-data-scripts",
    "构建与项目配置": "layer:build-and-project-config",
    "应用配置": "layer:application-config",
    "应用启动": "layer:application-bootstrap",
    "Web API 层": "layer:web-api",
    "业务服务层": "layer:business-services",
    "业务支撑管理层": "layer:business-managers",
    "数据访问层": "layer:data-access",
    "DTO 与传输模型": "layer:dto-models",
    "基础设施与横切能力": "layer:infrastructure-crosscutting",
    "核心公共能力": "layer:core-common",
    "定时任务与异步流程": "layer:scheduled-async",
    "测试与生成模板": "layer:tests-templates"
  };
  return map[name] || `layer:${name}`;
}

const scan = readJson(path.join(intermediateDir, "scan-result.json"));
const batches = readJson(path.join(intermediateDir, "batches.json"));
const files = scan.files.map(f => ({ ...f, path: rel(f.path) }));
const nodesById = new Map();
const edgesByKey = new Map();
const fileNodeIds = new Map();
const javaInfoByPath = new Map();
const simpleClassToPath = new Map();
const fqcnToPath = new Map();
const tableNodeByName = new Map();
const extractedByPath = new Map();

for (const name of fs.readdirSync(tmpDir).filter(f => /^extract-output-\d+\.json$/.test(f))) {
  const output = readJson(path.join(tmpDir, name));
  for (const result of output.results || []) {
    extractedByPath.set(rel(result.path), result);
  }
}

for (const file of files) {
  const id = nodeIdForFile(file);
  fileNodeIds.set(file.path, id);
  addNode({
    id,
    type: id.split(":")[0],
    name: basenameNoExt(file.path),
    filePath: file.path,
    summary: fileSummary(file.path, file.language, file.fileCategory),
    tags: fileTags(file.path, file.language, file.fileCategory),
    complexity: (file.sizeLines || 0) > 300 ? "complex" : (file.sizeLines || 0) > 120 ? "moderate" : "simple"
  });

  if (file.language === "java" && existsRel(file.path)) {
    const info = parseJava(readRel(file.path));
    javaInfoByPath.set(file.path, info);
    if (info.className) {
      simpleClassToPath.set(info.className, file.path);
      if (info.pkg) fqcnToPath.set(`${info.pkg}.${info.className}`, file.path);
    }
  }
}

for (const [filePath, result] of extractedByPath.entries()) {
  const fileId = fileNodeIds.get(filePath);
  if (!fileId) continue;
  for (const cls of result.classes || []) {
    const clsId = `class:${filePath}:${cls.name}`;
    addNode({
      id: clsId,
      type: "class",
      name: cls.name,
      filePath,
      startLine: cls.startLine,
      endLine: cls.endLine,
      summary: `${cls.name} 是 ${basenameNoExt(filePath)} 中定义的 Java 类型。`,
      tags: [...fileTags(filePath, result.language, result.fileCategory), "Class"]
    });
    addEdge(fileId, clsId, "contains", 1.0);
  }

  const layer = packageLayer(filePath);
  const keepFunctions = ["Web API 层", "业务服务层", "业务支撑管理层", "基础设施与横切能力", "定时任务与异步流程"].includes(layer);
  if (keepFunctions) {
    for (const fn of result.functions || []) {
      if (/^(get|set)[A-Z]/.test(fn.name) || fn.name === "toString" || fn.name === "equals" || fn.name === "hashCode") continue;
      const fnId = `function:${filePath}:${fn.name}`;
      addNode({
        id: fnId,
        type: "function",
        name: fn.name,
        filePath,
        startLine: fn.startLine,
        endLine: fn.endLine,
        summary: `${fn.name} 是 ${basenameNoExt(filePath)} 的关键方法，参与该模块的业务流程。`,
        tags: [...fileTags(filePath, result.language, result.fileCategory), "Method"]
      });
      addEdge(fileId, fnId, "contains", 1.0);
    }
  }

  for (const def of result.definitions || []) {
    if (def.kind !== "table") continue;
    const tableId = `table:${filePath}:${def.name}`;
    tableNodeByName.set(def.name.toLowerCase(), tableId);
    addNode({
      id: tableId,
      type: "table",
      name: def.name,
      filePath,
      startLine: def.startLine,
      endLine: def.endLine,
      summary: `${def.name} 是数据库表，字段包括 ${(def.fields || []).slice(0, 8).join(", ")}${(def.fields || []).length > 8 ? " 等" : ""}。`,
      tags: ["SQL", "Table", "数据模型"]
    });
    addEdge(fileId, tableId, "defines_schema", 0.8);
  }
}

for (const [filePath, info] of javaInfoByPath.entries()) {
  const sourceId = fileNodeIds.get(filePath);
  if (!sourceId) continue;
  for (const imp of info.imports) {
    const targetPath = fqcnToPath.get(imp) || simpleClassToPath.get(imp.split(".").pop());
    if (targetPath && targetPath !== filePath) {
      addEdge(sourceId, fileNodeIds.get(targetPath), "imports", 0.7);
      const sourceClass = info.className ? `class:${filePath}:${info.className}` : null;
      const targetInfo = javaInfoByPath.get(targetPath);
      const targetClass = targetInfo?.className ? `class:${targetPath}:${targetInfo.className}` : null;
      if (sourceClass && targetClass && nodesById.has(sourceClass) && nodesById.has(targetClass)) {
        addEdge(sourceClass, targetClass, "depends_on", 0.6);
      }
    }
  }

  if (filePath.includes("/service/impl/") && info.className?.endsWith("Impl")) {
    const serviceName = info.className.replace(/Impl$/, "");
    const servicePath = simpleClassToPath.get(serviceName);
    if (servicePath) addEdge(`class:${filePath}:${info.className}`, `class:${servicePath}:${serviceName}`, "implements", 0.9);
  }

  if (filePath.includes("/dao/entity/") && info.className) {
    const table = info.annotations.find(a => a.name === "TableName");
    const tableName = annotationValue(table?.value || "") || info.className.replace(/[A-Z]/g, (m, i) => `${i ? "_" : ""}${m.toLowerCase()}`);
    const tableId = tableNodeByName.get(tableName.toLowerCase());
    if (tableId) addEdge(`class:${filePath}:${info.className}`, tableId, "defines_schema", 0.8);
  }
}

for (const file of files.filter(f => f.path.endsWith(".xml") && f.path.includes("src/main/resources/mapper/"))) {
  const xml = existsRel(file.path) ? readRel(file.path) : "";
  const mapperName = basenameNoExt(file.path);
  const javaMapperPath = simpleClassToPath.get(mapperName);
  if (javaMapperPath) addEdge(fileNodeIds.get(javaMapperPath), fileNodeIds.get(file.path), "configures", 0.6);
  const tables = new Set([...xml.matchAll(/\b(?:from|join|into|update)\s+`?([a-zA-Z_][\w]*)`?/gi)].map(m => m[1].toLowerCase()));
  for (const table of tables) {
    const tableId = tableNodeByName.get(table);
    if (tableId) addEdge(fileNodeIds.get(file.path), tableId, "reads_from", 0.5);
  }
}

for (const [filePath, info] of javaInfoByPath.entries()) {
  if (!filePath.includes("/controller/")) continue;
  const sourceId = fileNodeIds.get(filePath);
  const content = readRel(filePath);
  const classRoute = annotationValue((content.match(/@RequestMapping\(([^)]*)\)/) || [])[1] || "");
  const methodRegex = /((?:@\w+(?:\([^)]*\))?\s*)+)\s*(?:public|private|protected)?\s+[\w<>, ?\[\]]+\s+(\w+)\s*\([^)]*\)/g;
  let match;
  while ((match = methodRegex.exec(content))) {
    const route = routeFromMethod(match[1]);
    if (!route) continue;
    const routePath = normalizeRoute(classRoute, route.path);
    const endpointId = `endpoint:${filePath}:${route.http} ${routePath}`;
    addNode({
      id: endpointId,
      type: "endpoint",
      name: `${route.http} ${routePath}`,
      filePath,
      summary: `${route.http} ${routePath} 路由到 ${basenameNoExt(filePath)}.${match[2]}，提供小说平台后端 HTTP API。`,
      tags: ["Endpoint", "Spring MVC", packageLayer(filePath)]
    });
    const fnId = `function:${filePath}:${match[2]}`;
    addEdge(endpointId, nodesById.has(fnId) ? fnId : sourceId, "routes", 0.5);
  }
}

for (const configPath of ["src/main/resources/application.yml", "src/main/resources/shardingsphere-jdbc.yml", "src/main/resources/logback-spring.xml", "pom.xml"]) {
  if (!fileNodeIds.has(configPath)) continue;
  addEdge(fileNodeIds.get(configPath), fileNodeIds.get("src/main/java/io/github/xxyopen/novel/NovelApplication.java"), "configures", 0.6);
}

const nodes = [...nodesById.values()];
const nodeIds = new Set(nodes.map(n => n.id));
const edges = [...edgesByKey.values()].filter(e => nodeIds.has(e.source) && nodeIds.has(e.target));
const layerBuckets = new Map();
for (const node of nodes.filter(n => ["file", "config", "document", "schema", "table", "endpoint"].includes(n.type))) {
  let name;
  if (node.type === "endpoint") name = "Web API 层";
  else if (node.type === "table") name = "数据访问层";
  else name = packageLayer(node.filePath || "");
  if (!layerBuckets.has(name)) layerBuckets.set(name, []);
  layerBuckets.get(name).push(node.id);
}

const layerDescriptions = {
  "文档与数据脚本": "项目说明、数据库初始化脚本和开发规范，帮助理解系统背景与数据结构。",
  "构建与项目配置": "Maven Wrapper、pom.xml 等构建入口，定义依赖、插件和项目元信息。",
  "应用配置": "Spring Boot、ShardingSphere、日志和 Mapper XML 等运行配置。",
  "应用启动": "Spring Boot 主启动类和应用引导入口。",
  "Web API 层": "前台读者端与作家端控制器，以及从 Spring MVC 映射提取出的 HTTP endpoint。",
  "业务服务层": "面向 Controller 的业务接口与实现，编排数据访问、缓存、搜索、消息和事务。",
  "业务支撑管理层": "缓存、Redis、DAO 聚合、消息发送等可复用业务支撑组件。",
  "数据访问层": "实体、Mapper 接口、Mapper XML 和 SQL 表定义，承载持久化模型与数据库访问。",
  "DTO 与传输模型": "请求、响应、Elasticsearch 文档和跨层传输模型。",
  "基础设施与横切能力": "认证授权、拦截器、过滤器、AOP 切面、框架配置和监听器。",
  "核心公共能力": "常量、注解、通用工具、统一异常、请求响应包装等基础代码。",
  "定时任务与异步流程": "后台任务和异步同步逻辑。",
  "测试与生成模板": "测试资源和代码生成模板，用于验证或辅助开发。"
};

const preferredLayerOrder = [
  "文档与数据脚本",
  "构建与项目配置",
  "应用配置",
  "应用启动",
  "Web API 层",
  "业务服务层",
  "业务支撑管理层",
  "数据访问层",
  "DTO 与传输模型",
  "基础设施与横切能力",
  "核心公共能力",
  "定时任务与异步流程",
  "测试与生成模板"
];
const layers = preferredLayerOrder
  .filter(name => layerBuckets.has(name))
  .map(name => ({ id: layerId(name), name, description: layerDescriptions[name], nodeIds: layerBuckets.get(name).sort() }));

function ids(...values) {
  return values.filter(id => id && nodeIds.has(id));
}

const tour = [
  {
    order: 1,
    title: "先读项目目标与构建入口",
    description: "从 README 和 pom.xml 建立项目背景：这是一个 Spring Boot 小说平台后端，依赖 MyBatis-Plus、Redis、Elasticsearch、RabbitMQ、XXL-JOB 等能力。",
    nodeIds: ids("document:README.md", "config:pom.xml")
  },
  {
    order: 2,
    title: "理解应用启动和运行配置",
    description: "查看 Spring Boot 启动类和 application.yml，确认应用如何加载数据库、缓存、搜索、消息、接口文档和自定义业务配置。",
    nodeIds: ids("file:src/main/java/io/github/xxyopen/novel/NovelApplication.java", "config:src/main/resources/application.yml")
  },
  {
    order: 3,
    title: "沿 HTTP API 进入业务流程",
    description: "从前台与作家端 Controller 进入系统，观察 Spring MVC endpoint 如何路由到控制器方法。",
    nodeIds: nodes.filter(n => n.type === "endpoint").slice(0, 12).map(n => n.id)
  },
  {
    order: 4,
    title: "跟进 Service 编排",
    description: "业务服务层连接 Controller、Manager、Mapper 和外部能力，是理解小说、用户、作者、新闻、搜索等流程的主线。",
    nodeIds: ids(
      "file:src/main/java/io/github/xxyopen/novel/service/BookService.java",
      "file:src/main/java/io/github/xxyopen/novel/service/impl/BookServiceImpl.java",
      "file:src/main/java/io/github/xxyopen/novel/service/UserService.java",
      "file:src/main/java/io/github/xxyopen/novel/service/impl/UserServiceImpl.java"
    )
  },
  {
    order: 5,
    title: "查看数据访问模型",
    description: "DAO Entity、Mapper 接口、Mapper XML 和 SQL 表定义共同描述持久化边界，适合用来理解核心业务对象和数据库表。",
    nodeIds: ids(
      "file:src/main/java/io/github/xxyopen/novel/dao/entity/BookInfo.java",
      "file:src/main/java/io/github/xxyopen/novel/dao/mapper/BookInfoMapper.java",
      "config:src/main/resources/mapper/BookInfoMapper.xml",
      tableNodeByName.get("book_info")
    )
  },
  {
    order: 6,
    title: "补齐横切与基础设施",
    description: "认证、限流、XSS、锁、序列化、缓存、搜索和消息配置解释了业务代码之外的运行时行为。",
    nodeIds: ids(
      "file:src/main/java/io/github/xxyopen/novel/core/config/WebConfig.java",
      "file:src/main/java/io/github/xxyopen/novel/core/interceptor/AuthInterceptor.java",
      "file:src/main/java/io/github/xxyopen/novel/core/aspect/LockAspect.java",
      "file:src/main/java/io/github/xxyopen/novel/core/config/CacheConfig.java"
    )
  }
].filter(step => step.nodeIds.length);

let gitCommitHash = "no-git";
try {
  gitCommitHash = cp.execFileSync("git", ["rev-parse", "HEAD"], { cwd: projectRoot, encoding: "utf8" }).trim();
} catch {}

const graph = {
  version: "1.0.0",
  project: {
    name: "novel",
    languages: [...new Set(files.map(f => f.language).filter(Boolean))].sort(),
    frameworks: ["Spring Boot", "Spring MVC", "MyBatis-Plus", "MyBatis XML", "Redis", "Elasticsearch", "RabbitMQ", "ShardingSphere", "XXL-JOB", "Maven"],
    description: "小说平台后端服务，提供读者端、作家端、搜索、缓存、消息、认证和数据访问等能力。",
    analyzedAt: new Date().toISOString(),
    gitCommitHash
  },
  nodes,
  edges,
  layers,
  tour
};

fs.writeFileSync(path.join(intermediateDir, "assembled-graph.json"), JSON.stringify(graph, null, 2), "utf8");
fs.writeFileSync(path.join(uaDir, "knowledge-graph.json"), JSON.stringify(graph, null, 2), "utf8");
fs.writeFileSync(path.join(uaDir, "meta.json"), JSON.stringify({
  lastAnalyzedAt: graph.project.analyzedAt,
  gitCommitHash,
  version: "1.0.0",
  analyzedFiles: files.length
}, null, 2), "utf8");

const sourceFilePaths = files.filter(f => f.fileCategory === "code").map(f => f.path);
fs.writeFileSync(path.join(intermediateDir, "fingerprint-input.json"), JSON.stringify({
  projectRoot,
  sourceFilePaths,
  gitCommitHash
}, null, 2), "utf8");

const summary = {
  files: files.length,
  fileCategories: files.reduce((a, f) => (a[f.fileCategory] = (a[f.fileCategory] || 0) + 1, a), {}),
  nodes: nodes.length,
  nodeTypes: nodes.reduce((a, n) => (a[n.type] = (a[n.type] || 0) + 1, a), {}),
  edges: edges.length,
  edgeTypes: edges.reduce((a, e) => (a[e.type] = (a[e.type] || 0) + 1, a), {}),
  layers: layers.map(l => l.name),
  tourSteps: tour.length,
  gitCommitHash
};
fs.writeFileSync(path.join(intermediateDir, "build-summary.json"), JSON.stringify(summary, null, 2), "utf8");
console.log(JSON.stringify(summary, null, 2));
