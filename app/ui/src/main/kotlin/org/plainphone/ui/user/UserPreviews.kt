/* Copyright 2026 Plainphone contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.plainphone.ui.user

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.plainphone.ui.theme.PlainphoneTheme

@Preview(widthDp = 360, heightDp = 640, fontScale = 1f)
@Preview(widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable private fun MorePreview() = PlainphoneTheme {
    MoreScreen(MoreUiState(MoreMode.Trusted(listOf("Sam", "Jo"), "Sam")), {})
}
@Preview(widthDp = 360, heightDp = 640, fontScale = 1f)
@Preview(widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable private fun AskPreview() = PlainphoneTheme { AskForAppScreen(AskForAppUiState("Sam"), {}) }
@Preview(widthDp = 360, heightDp = 640, fontScale = 1f)
@Preview(widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable private fun RequestResultPreview() = PlainphoneTheme {
    AppRequestResultScreen(AppRequestResultUiState("Sam", AppRequestResult.Failed), {})
}
@Preview(widthDp = 360, heightDp = 640, fontScale = 1f)
@Preview(widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable private fun LinkPreview() = PlainphoneTheme {
    BlockedLinkScreen(BlockedLinkUiState.Blocked("example.invalid", shareWith = "Sam"), {})
}
@Preview(widthDp = 360, heightDp = 640, fontScale = 1f)
@Preview(widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable private fun WifiPreview() = PlainphoneTheme {
    WifiMessageScreen(WifiMessageUiState(WifiProblem.SignInRequired), {})
}
