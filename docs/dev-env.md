# 开发环境说明（.dev-env）

本项目的全部开发工具链**临时部署**在 `.dev-env/` 内，不污染系统环境；
项目开发完毕后可整体删除。

## 内容

| 目录 | 内容 | 来源 |
|---|---|---|
| `git/` | PortableGit（git.exe / bash） | npmmirror（git-for-windows 镜像） |
| `jdk/` | OpenJDK 21（Temurin） | 清华 Adoptium 镜像 |
| `gradle/` | Gradle 发行版 | services.gradle.org |
| `android-sdk/` | Android 命令行工具 + SDK 包 | dl.google.com |
| `gradle-home/` | Gradle 缓存（依赖、wrapper） | 构建时自动生成 |
| `downloads/` | 安装包缓存 | — |
| `env.ps1` | 一次性设置所有环境变量 | — |

## 用法

每个新终端里先执行：

```powershell
. D:\Fish\documents\jianji\.dev-env\env.ps1
```

然后即可使用 `git` / `java` / `gradle` / `adb` / `sdkmanager`。

## 清理（项目完成后）

```powershell
Remove-Item -Recurse -Force D:\Fish\documents\jianji\.dev-env
```

`reference/`（外部参考仓库克隆）与 `~/.gradle`（若系统里另有 Gradle 缓存）
按需另行清理；`local.properties`、`.gradle/`、`build/` 可随项目仓库保留或删除。
