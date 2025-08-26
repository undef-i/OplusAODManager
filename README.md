# OplusAODManager

通过 LSPosed 框架为 ColorOS / OxygenOS 提供高度自定义的息屏显示 (AOD)。目前仅在 `PKX110_15.0.2.500` 测试通过。

## 核心概念

息屏界面通过 JSON 数组定义，数组中的每个对象代表一个独立的视图元素（如文本、时钟等）。模块根据 JSON 动态构建布局。

### 视图对象属性

- `type` (字符串): (必需) 视图的完整类名，通过反射创建。
  - 常用值: `android.widget.TextView`, `android.widget.TextClock`, `android.widget.ImageView`, `android.widget.AnalogClock`, `android.widget.ProgressBar`

- `id` (字符串): 视图的唯一标识，用于 `layout_rules` 相对定位。

- `width`, `height` (数字): 视图的宽高 (单位: dp)。

- `alpha` (数字): 透明度 (范围: 0.0 - 1.0)。

- `marginLeft`, `marginRight`, `marginTop`, `marginBottom` (数字): 外边距 (单位: dp)。

- `layout_rules` (对象): (核心) 定义视图位置的规则集，详见下文。

- `text` (字符串): 静态文本内容。

- `textColor` (字符串): 文本颜色，例如 `#FFFFFFFF`。

- `textSize` (数字): 文本大小 (单位: sp)。  

- `textStyle` (字符串): 文本样式，可选值: `normal`, `italic`, `bold`, `bold_italic`。

- `random_texts` (字符串数组): 每次随机显示数组中的一个字符串。

- `tag` (字符串): 动态数据标签，用于显示实时信息。
  - `data:date`: 当前日期。
  - `data:battery_level`: 电量百分比。
  - `data:battery_charging`: 仅在充电时显示。
  - `data:user_image_random`: 随机显示一张用户图片。
  - `data:user_image[N]`: 显示第 N 张用户图片 (N 从 0 开始)。

- `format24Hour`, `format12Hour` (字符串): TextClock 专用，定义 24/12 小时制时间格式，例如 `HH:mm`。

- `scaleType` (字符串): ImageView 专用，图片缩放类型，例如 `centerCrop`。

- `progress_tag` (字符串): ProgressBar 专用，用于绑定数据，例如 `data:battery_level`。

### layout_rules 定位规则

#### 相对于屏幕定位

`centerInParent`, `centerHorizontal`, `centerVertical`, `alignParentTop`, `alignParentBottom`, `alignParentLeft`, `alignParentRight`

#### 相对于其他视图定位

`below`, `above`, `toLeftOf`, `toRightOf` (值为另一个视图的 `id` 字符串)

