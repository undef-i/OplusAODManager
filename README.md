# OplusAODManager

基于 LSPosed 框架的 ColorOS/OxygenOS 息屏显示 (AOD) 自定义工具。已在 `PKX110_15.0.2.500` 测试通过。

## 视图配置

每个视图对象遵循以下配置规范：

### 基础属性

- `type` (`字符串`): 必填，视图完整类名
  - 常用：`TextView`, `TextClock`, `ImageView`, `AnalogClock`, `ProgressBar`
- `id` (`字符串`): 视图唯一标识符，用于相对定位

### 尺寸边距

- `width`, `height` (`数字`): 宽高 (dp)
- `marginLeft`, `marginRight`, `marginTop`, `marginBottom` (`数字`): 外边距 (dp)

### 数据绑定

- `tag` (`字符串`): 动态数据源
  - `data:date`: 当前日期
  - `data:battery_level`: 电量百分比
  - `data:battery_charging`: 仅充电时显示
  - `data:user_image_random`: 随机用户图片
  - `data:user_image[N]`: 第 N 张用户图片 (从 0 开始)
- `progress_tag` (`字符串`): ProgressBar 进度绑定 (`data:battery_level`)

### 通用属性

其他属性自动映射到视图 `set...` 方法：

- JSON 属性 `someProperty` → 调用 `setSomeProperty(...)`
- 示例：
  - `"textColor": "#FFFF00"` → `setTextColor(int)`
  - `"rotation": 45` → `setRotation(float)`
  - `"textSize": 22` → `setTextSize(float)`

常用属性：
- 通用：`alpha`, `rotation`, `padding`
- 文本：`text`, `textColor`, `textSize`, `gravity`, `letterSpacing`
- 图片：`scaleType`
- 时钟：`format12Hour`, `format24Hour`

## 布局规则 (`layout_rules`)

定义视图屏幕位置：

- 相对屏幕：`centerInParent`, `centerHorizontal`, `centerVertical`, `alignParentTop`, `alignParentBottom`, `alignParentLeft`, `alignParentRight` (值：`true`)
- 相对视图：`below`, `above`, `toLeftOf`, `toRightOf` (值：目标视图 `id`)

