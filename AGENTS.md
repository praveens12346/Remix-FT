# FinTrace Background & Widget Architectures

This file documents the robust synchronization and widget configurations implemented in FinTrace.

## 1. Multi-Widget Ecosystem
We support three home screen widgets:
*   **FinTrace Live Prices (`PriceWidgetProvider`)**: Displays the general top-performing asset pairs.
*   **FinTrace Target Asset (`PriceWidgetSingleProvider`)**: A highly styled, interactive widget showcasing a single selected target asset. Supported by full custom asset selection.
*   **FinTrace 5 Tracker (`PriceWidgetFiveProvider`)**: Displays up to five custom-selected assets in a dense, compact list view.

### Configuration Protocol
All widgets share a unified, high-performance Jetpack Compose config environment:
*   **`WidgetConfigActivity.kt`**: Launches upon addition. It automatically queries the system widget manager, identifies whether it is configuring a single or 5-asset layout, and renders an AMOLED-styled asset selector matching the client theme.
*   **Storage**: Asset arrays are stored as comma-delimited keys under `"fintrace_widgets"` private SharedPreferences (indexed by the unique OS-assigned `appWidgetId`).

## 2. Notification and pricing synchronization
To resolve the live pricing desynchronization issues in the notification shade:
1.  **Setting-Driven Update Logic**: Instead of using generic asset caches, the background tracking loops query the user's explicit `"live_ticker_symbols"` choice.
2.  **Toggle-Off Responsiveness**: If `"live_ticker_symbols"` is empty (meaning the user toggled the notification shade ticker off), the notification manager instantly cancels the active persistent notification, drastically lowering battery overhead.
3.  **Unified Thread Ticking**: Price changes cascading from both the Twelve Data WebSocket and simulated randomized price walks instantly push updates to standard notifications and both interactive widgets in parallel.
