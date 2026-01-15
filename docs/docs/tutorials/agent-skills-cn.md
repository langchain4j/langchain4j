# LangChain4j Agent Skills (代理技能)

> **状态**: 实验性功能 (1.12.0)

通过 `SKILL.md` 文件动态扩展 AI 代理的能力。让代理能够执行脚本、读取资源和使用外部工具来完成复杂任务。

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-agent-skills</artifactId>
    <version>1.12.0-SNAPSHOT</version>
</dependency>
```

### 2. 创建技能

创建 `skills/calculator/SKILL.md`:

```markdown
---
name: calculator
description: 执行数学计算
allowed-tools:
  - python
---

# 计算器技能

使用 `python scripts/calc.py <表达式>` 来计算数学表达式。
```

创建 `skills/calculator/scripts/calc.py`:

```python
#!/usr/bin/env python3
import sys
print(eval(sys.argv[1]))
```

赋予执行权限:
```bash
chmod +x skills/calculator/scripts/calc.py
```

### 3. 配置代理

```java
import dev.langchain4j.agentskills.AgentSkillsConfig;
import dev.langchain4j.agentskills.DefaultAgentSkillsProvider;
import dev.langchain4j.service.AiServices;
import java.nio.file.Path;

AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(DefaultAgentSkillsProvider.builder()
        .skillDirectories(Path.of("skills"))
        .build())
    .maxIterations(10)
    .build();

interface Assistant {
    String chat(String message);
}

Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(yourChatModel)
    .agentSkillsConfig(config)  // ← 启用 Agent Skills
    .build();
```

### 4. 使用！

```java
String result = assistant.chat("计算 15 * 23");
// LLM 自动使用计算器技能: "结果是 345"
```

---

## 工作原理

### 1. 系统提示增强
发送消息时，Agent Skills 自动将可用技能添加到系统提示中：

```xml
<available_skills>
  <skill>
    <name>calculator</name>
    <description>执行数学计算</description>
  </skill>
</available_skills>

使用说明:
- 加载技能: <use_skill>skill-name</use_skill>
- 执行脚本: <execute_script skill="skill-name">command</execute_script>
- 读取资源: <read_resource skill="skill-name">path</read_resource>
```

### 2. LLM 响应处理
LLM 可以在响应中包含指令：

```xml
<execute_script skill="calculator">python scripts/calc.py 15*23</execute_script>
```

Agent Skills 自动：
1. 根据 `allowed-tools` 验证命令
2. 在技能目录中执行脚本
3. 捕获输出并发送回 LLM
4. LLM 生成最终回复给用户

### 3. 自动迭代
此过程持续进行，直到 LLM 返回用户可见的消息（最多 `maxIterations` 次）。

---

## 技能定义

### SKILL.md 格式

```markdown
---
name: skill-name                    # 必需: 小写字母和连字符
description: 简短描述               # 必需: ≤ 500 字符
license: MIT                        # 可选
compatibility: 适用于 GPT-4         # 可选: ≤ 200 字符
allowed-tools:                      # 可选: 命令白名单
  - python
  - bash
  - scripts/*                       # 支持通配符
metadata:                           # 可选: 自定义字段
  author: 你的名字
  version: 1.0.0
---

# 技能文档

这里是 Markdown 格式的文档...
使用说明...
```

### 目录结构

```
skills/
└── skill-name/
    ├── SKILL.md           # 必需: 元数据 + 文档
    ├── scripts/           # 可选: 可执行脚本
    │   ├── script1.sh
    │   └── script2.py
    └── assets/            # 可选: 配置/资源
        ├── config.json
        └── template.txt
```

---

## 指令参考

### 1. 加载技能内容

**指令**: `<use_skill>skill-name</use_skill>`

**用途**: 加载完整的 `SKILL.md` 内容（不含 frontmatter）

**响应**:
```xml
<skill_content name="skill-name">
...技能说明...
</skill_content>
```

**使用场景**: LLM 需要详细说明才能使用技能

---

### 2. 执行脚本

**指令**: `<execute_script skill="skill-name">command args</execute_script>`

**用途**: 在技能目录中运行脚本或命令

**安全性**:
- 命令必须在 `allowed-tools` 列表中
- 在技能目录作为工作目录执行
- 60 秒超时（可配置）

**响应**:
```xml
<script_result exit_code="0">
标准输出内容
STDERR:
标准错误内容（如果有）
</script_result>
```

**使用场景**: 执行处理脚本、运行计算、获取数据

---

### 3. 读取资源

**指令**: `<read_resource skill="skill-name">relative/path.txt</read_resource>`

**用途**: 从技能目录读取文件

**安全性**:
- 路径必须是相对路径（无绝对路径）
- 阻止路径遍历（技能目录外的 `../`）
- 防止符号链接逃逸

**响应**:
```xml
<resource_content path="relative/path.txt">
文件内容
</resource_content>
```

**使用场景**: 加载配置文件、模板、查询数据

---

## 安全性

### 命令白名单

**问题**: 防止任意命令执行

**解决方案**: SKILL.md 中的 `allowed-tools`

```yaml
allowed-tools:
  - python              # 允许: python script.py
  - bash                # 允许: bash script.sh
  - scripts/*           # 允许: scripts/任何内容
  - "*"                 # 允许: 所有（谨慎使用！）
```

**验证**: 精确匹配或通配符前缀匹配

---

### 路径遍历防护

**问题**: 访问技能目录外的文件

**解决方案**: 多层验证
1. 解析前规范化路径
2. 检查 `startsWith(skillPath)`
3. 解析真实路径（跟随符号链接）
4. 再次检查 `startsWith(skillPath)`

**阻止**:
```
../../../etc/passwd          ❌
/tmp/file.txt                ❌
symlink-to-outside-dir       ❌
```

**允许**:
```
assets/config.json           ✅
scripts/helper.py            ✅
./relative/path.txt          ✅
```

---

## 配置

### AgentSkillsConfig 构建器

```java
AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(provider)           // 必需
    .scriptExecutor(customExecutor)     // 可选: 默认是 DefaultScriptExecutor
    .maxIterations(10)                  // 可选: 默认是 10
    .build();
```

### DefaultAgentSkillsProvider 构建器

```java
AgentSkillsProvider provider = DefaultAgentSkillsProvider.builder()
    .skillDirectories(
        Path.of("skills"),              // 从多个目录加载
        Path.of("/opt/shared-skills")
    )
    .build();

// 修改后重新加载技能
provider.reload();
```

### 自定义脚本执行器

```java
ScriptExecutor dockerExecutor = (workingDir, command) -> {
    // 在 Docker 容器中运行脚本
    ProcessBuilder pb = new ProcessBuilder(
        "docker", "run", "--rm",
        "-v", workingDir + ":/workspace",
        "-w", "/workspace",
        "python:3.11-alpine",
        "sh", "-c", command
    );
    Process process = pb.start();
    // ... 捕获输出 ...
    return new ScriptExecutionResult(
        process.waitFor(),
        stdout,
        stderr
    );
};

AgentSkillsConfig config = AgentSkillsConfig.builder()
    .skillsProvider(provider)
    .scriptExecutor(dockerExecutor)      // 使用自定义执行器
    .build();
```

---

## 示例

### 示例 1: 网页抓取技能

**技能**: `skills/web-scraper/SKILL.md`
```markdown
---
name: web-scraper
description: 获取和解析网页内容
allowed-tools:
  - curl
  - python
---

# 网页抓取器

获取 URL:
```bash
curl -s https://example.com
```

解析 HTML:
```bash
python scripts/parse.py <url>
```
```

**使用**:
```java
assistant.chat("example.com 首页有什么内容？");
// LLM 使用 web-scraper 技能获取和分析页面
```

---

### 示例 2: 数据分析技能

**技能**: `skills/data-analyzer/SKILL.md`
```markdown
---
name: data-analyzer
description: 分析 CSV 数据并生成洞察
allowed-tools:
  - python
---

# 数据分析器

分析 CSV 文件:
```bash
python scripts/analyze.py <csv-file>
```

配置在 `assets/config.json`:
- `threshold`: 过滤的最小值
- `chart_type`: 可视化类型
```

**资源**: `skills/data-analyzer/assets/config.json`
```json
{
  "threshold": 0.5,
  "chart_type": "bar"
}
```

**使用**:
```java
assistant.chat("分析 sales_data.csv 找出热销产品");
// LLM:
// 1. <read_resource skill="data-analyzer">assets/config.json</read_resource>
// 2. <execute_script skill="data-analyzer">python scripts/analyze.py sales_data.csv</execute_script>
// 3. 为用户总结结果
```

---

## 测试

### 单元测试

运行所有单元测试:
```bash
mvn test -pl langchain4j-agent-skills
```

测试特定组件:
```bash
mvn test -Dtest=FileSystemSkillLoaderTest -pl langchain4j-agent-skills
```

### 端到端测试

需要真实 LLM（如通义千问）:
```bash
export QWEN_API_KEY="your-api-key"
mvn test -Dtest=AgentSkillsEndToEndTest -pl langchain4j-agent-skills
```

**测试覆盖率**:
- 66 个测试 (9 + 11 + 13 + 26 + 7)
- 100% 通过率
- 与真实 LLM 的完整集成

---

## 故障排除

### 技能未加载

**检查**:
1. 技能目录中存在 `SKILL.md`
2. frontmatter 中的技能名称与目录名称匹配
3. YAML frontmatter 有效（使用 YAML 检查器）
4. 检查日志中的解析错误

**调试**:
```java
provider.provideSkills(request).skills().forEach(skill ->
    System.out.println("已加载: " + skill.name())
);
```

---

### 脚本执行失败

**检查**:
1. 脚本有执行权限: `chmod +x scripts/script.sh`
2. Shebang 正确: `#!/bin/bash` 或 `#!/usr/bin/env python3`
3. 命令在 `allowed-tools` 列表中
4. 脚本在技能目录中

---

### LLM 未使用技能

**尝试**:
1. 更明确的提示: "使用计算器技能来计算..."
2. 检查系统提示增强（启用调试日志）
3. 如果过程提前停止，增加 `maxIterations`
4. 尝试不同的 LLM 模型（GPT-4、Claude 等）
5. 验证模型支持工具/函数调用模式

---

## 架构

详细的架构、设计决策和实现细节，请参阅:
- **[agent-skills-architecture.md](agent-skills-architecture.md)** - 完整设计文档（英文）

**核心组件**:
- `AgentSkillsService` - 编排技能操作
- `DefaultAgentSkillsProvider` - 从文件系统加载技能
- `FileSystemSkillLoader` - 解析 SKILL.md 文件
- `DefaultScriptExecutor` - 执行 shell 命令
- `AgentSkillsConfig` - 配置容器

---

## 当前限制

1. **仅文件系统**: 技能必须在本地文件系统上（尚无 HTTP/数据库加载器）
2. **单条指令**: 每次迭代仅处理 LLM 响应中的第一条指令
3. **无流式处理**: 脚本输出在完成后捕获（无实时流）
4. **Unix 为主**: 最佳支持 Unix 类系统（macOS、Linux）
5. **无依赖管理**: 技能不能依赖其他技能

---

## 路线图

- [ ] 数据库支持的技能提供者
- [ ] HTTP API 技能加载器
- [ ] 技能依赖解析
- [ ] 基于 Docker 的沙箱
- [ ] 流式脚本输出
- [ ] 技能市场/注册表
- [ ] Windows 原生脚本支持
- [ ] 技能组合 DSL

---

## 贡献

欢迎贡献！详见 [agent-skills-architecture.md](agent-skills-architecture.md)

---

## 许可证

Apache 2.0（与 LangChain4j 相同）

---

## 支持

**Issues**: [GitHub Issues](https://github.com/langchain4j/langchain4j/issues)
**讨论**: [GitHub Discussions](https://github.com/langchain4j/langchain4j/discussions)
**作者**: Shrink (shunke.wjl@alibaba-inc.com)

---

## 另请参阅

- [LangChain4j 文档](https://docs.langchain4j.dev)
- [AI Services 指南](https://docs.langchain4j.dev/tutorials/ai-services)
- [工具系统](https://docs.langchain4j.dev/tutorials/tools)
