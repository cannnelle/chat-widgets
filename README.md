# Chat Widgets

**Version 1.0.1**

Displays game chat and private chat messages in their own customizable widgets. Supports custom colours, repositioning, game message collapsing, font size, and more.

This plugin was created due to my own desire for a moveable private chat message and to peek at 1-2 game messages while my chat box is minimized.

## Examples

Display options are quite flexible. Below are some configuration examples.

### Positioning

**Standard Overlay**

- **Game Messages:** Position set to "Default" (user-specified) with a bottom margin applied to clear infoboxes.
- **Private Messages:** Anchored to the top-left with a top margin applied.

![default](https://github.com/user-attachments/assets/ebfb08a7-c049-480c-ad75-618f8cf4c056)

**Below Player**

- **Position:** Below Player (Offset: 0)
- **Fade Out Duration:** 3s
- **Max Messages:** 1

<video src="https://github.com/user-attachments/assets/52c2c0c1-4817-4231-87cf-a54ffa6c6245" style="width: 100%"></video>

**Above Player**

- **Position:** Above Player (Offset: 0)
- **Fade Out Duration:** 3s
- **Max Messages:** 1

<video src="https://github.com/user-attachments/assets/61abed62-1deb-4149-988c-a731826156f1" style="width: 100%"></video>

## Configuration

### General Settings

| Setting          | Description                                                                                                                                            |
| :--------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Enable**       | Enable/disable the widget. Game messages only render when the chatbox is minimized. Private messages also hide the client's split private chat widget. |
| **Max Messages** | Maximum number of messages visible in the widget (1-20).                                                                                               |
| **Text Colour**  | Base colour for message text. Supports alpha transparency.                                                                                             |
| **Background**   | Background colour of the widget. Set alpha to 0 for transparent.                                                                                       |

### Appearance (Shared)

| Setting                 | Description                                                                                                                   |
| :---------------------- | :---------------------------------------------------------------------------------------------------------------------------- |
| **Font Size**           | Font size for all messages (Small, Regular).                                                                                  |
| **Merge Chat Widgets**  | Renders game and private messages in a single widget. Ignored if game messages are disabled or positioned relative to player. |
| **Swap Stacking Order** | Swap which widget renders on top when not merged.                                                                             |
| **Smart Positioning**   | Automatically reposition widgets based on client mode and chatbox state.                                                      |
| **Wrap Text**           | Wrap long messages to multiple lines instead of truncating.                                                                   |
| **Text Shadow**         | Draw a shadow behind text for better readability.                                                                             |
| **Show Timestamps**     | Prefix messages with a timestamp.                                                                                             |
| **Timestamp Format**    | Format string for timestamps (e.g., `[HH:mm:ss]`, `[HH:mm]`).                                                                 |

### Game Messages

| Setting                  | Description                                                                                                                                                                                      |
| :----------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Position**             | Widget position mode. `Default` uses standard overlay positioning. `Below Player` and `Above Player` position the widget relative to your character (works best with fade and low max messages). |
| **Contextual Colours**   | Retain colour formatting from in-game messages (e.g., item names, quest text).                                                                                                                   |
| **Collapse Duplicates**  | Combine identical consecutive messages with a count indicator.                                                                                                                                   |
| **Hide Duplicate Count** | Hide the count badge when collapsing duplicates.                                                                                                                                                 |
| **Player Offset**        | Vertical offset when positioned relative to player (-50 to 50).                                                                                                                                  |

### Advanced Settings

| Setting               | Description                                                                                                 |
| :-------------------- | :---------------------------------------------------------------------------------------------------------- |
| **Dynamic Height**    | Widget height adjusts based on message count rather than using fixed height.                                |
| **Fade Out Duration** | Seconds before messages start fading out (0 = never fade). Messages fully disappear after 2x this duration. |
| **Widget Width**      | Width of the widget in pixels (150-1024).                                                                   |
| **Margin Top/Bottom** | Extra spacing above/below the widget.                                                                       |

## Tips

If you want to anchor a chat widget in the bottom left above the chatbox, use the anchor fixed to the **right** of the chatbox. This anchor point stacks widgets vertically, unlike the bottom left which stacks widgets horizontally.

<img width="1036" height="250" alt="image" src="https://github.com/user-attachments/assets/fa24e015-b43d-48ef-90b9-e843fc3651ce" />
