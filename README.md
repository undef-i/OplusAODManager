# OplusAODManager

ColorOS AOD 文本自定义模块，通过 LSPosed 框架自定义 AOD 。目前仅在 `PKX110_15.0.2.500` 测试通过。


## 自定义布局

布局是一个JSON数组 `[ ]`，其中每个对象 `{ }` 代表一个视图元素。

### 视图对象 `{ }` 属性

  * `type`: (字符串) 视图类型。
  * `id`: (字符串) 视图的唯一标识符，用于相对定位。
  * `width`: (数字) 宽度，单位dp。
  * `height`: (数字) 高度，单位dp。
  * `alpha`: (数字) 透明度，范围 0.0 到 1.0。
  * `marginTop`: (数字) 顶部外边距, dp。
  * `marginLeft`: (数字) 左侧外边距, dp。
  * `marginRight`: (数字) 右侧外边距, dp。
  * `marginBottom`: (数字) 底部外边距, dp。
  * `text`: (字符串) 显示的静态文本。
  * `textColor`: (字符串) 文本颜色，十六进制代码。例: `"#FFFFFFFF"`。
  * `textSize`: (数字) 文本大小。
  * `textStyle`: (字符串) 文本样式。
  * `random_texts`: (数组) 字符串数组，每次随机显示其中一个。
  * `tag`: (字符串) 用于显示动态数据。
  * `layout_rules`: (对象) 定义视图的位置规则。

### `layout_rules` 对象属性

  * 相对于屏幕定位 (值为 `true`):

      * `centerInParent`
      * `centerHorizontal`
      * `centerVertical`
      * `alignParentTop`
      * `alignParentBottom`
      * `alignParentLeft`
      * `alignParentRight`

  * 相对于其他视图定位 (值为另一个视图的 `id` 字符串):

      * `below`
      * `above`
      * `toRightOf`
      * `toLeftOf`
      * `alignBaseline`

### 可用值列表

  * `type` 可用值:

      * `"TextView"`
      * `"TextClock"`
      * `"ImageView"`

  * `tag` 可用值:

      * `"data:date"`: 显示日期。
      * `"data:battery_level"`: 显示电量。
      * `"data:battery_charging"`: 充电时才显示此视图。
      * `"data:user_image"`: 显示用户选择的图片。

  * `textStyle` 可用值:

      * `"bold"`
      * `"italic"`
      * `"bold_italic"`

  * `TextClock` 专属属性:

      * `format24Hour`: (字符串) 24小时制格式。例: `"HH:mm"`。
      * `format12Hour`: (字符串) 12小时制格式。例: `"hh:mm a"`。

  * `ImageView` 专属属性:

      * `scaleType`: (字符串) 图片缩放类型。例: `"centerCrop"`, `"fitCenter"`。
