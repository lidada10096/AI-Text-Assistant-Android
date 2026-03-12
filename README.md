# AI文本助手 - Android版

一款基于Android无障碍服务的智能文本补全与问答工具。

## 功能特点

- 🤖 **文本补全**：选中文本后智能补全内容
- 💬 **智能问答**：针对选中文本进行AI问答
- 🎯 **无障碍服务**：通过Android无障碍服务检测用户操作
- 🔄 **浮动按钮**：在其他应用上方显示操作按钮
- ⚙️ **高度可配置**：支持自定义API、模型、提示词等参数
- 🎨 **深色主题**：现代化的深色UI设计

## 系统要求

- Android 8.0 (API 26) 或更高版本
- 需要开启无障碍服务权限
- 需要悬浮窗权限（用于显示浮动按钮）

## 安装方法

### 方法一：直接安装APK

1. 下载 `app-release.apk` 文件
2. 在Android设备上安装APK
3. 按照应用内指引开启必要权限

### 方法二：从源码构建

1. 克隆项目代码
2. 使用 Android Studio 打开项目
3. 同步Gradle并构建项目
4. 运行到设备或生成APK

```bash
# 构建Release版本
./gradlew assembleRelease

# 构建Debug版本
./gradlew assembleDebug
```

## 使用说明

### 首次使用

1. **开启无障碍服务**
   - 打开应用，点击"启用无障碍服务"
   - 在系统设置中找到"AI文本助手"
   - 开启无障碍服务

2. **配置API设置**
   - 进入设置页面
   - 填写API Key和Base URL
   - 选择模型（可选）
   - 测试连接

3. **使用功能**
   - 在任何应用中选中文本
   - 点击出现的浮动按钮
   - 选择"补全"或"问答"
   - 查看AI生成的结果

## 项目结构

```
android-app/
├── app/src/main/
│   ├── java/com/aitextassistant/
│   │   ├── service/     # 无障碍服务、浮动按钮、AI服务
│   │   ├── ui/          # 主界面、设置界面
│   │   ├── data/        # 偏好设置管理
│   │   └── model/       # 数据模型
│   └── res/             # 资源文件
└── build.gradle.kts     # 构建配置
```

## 技术栈

- **语言**：Kotlin
- **UI框架**：Android Material Design 3
- **网络**：OkHttp
- **序列化**：Kotlinx Serialization
- **协程**：Kotlin Coroutines

## 许可证

MIT License
