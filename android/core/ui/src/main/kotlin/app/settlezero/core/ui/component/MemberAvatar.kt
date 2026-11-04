package app.settlezero.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.settlezero.core.model.MemberRole

@Composable
fun MemberAvatar(
    name: String,
    role: MemberRole? = null,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val initials = name.trim()
        .split("\\s+".toRegex())
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifEmpty { "?" }

    Box(
        modifier = modifier.size(size)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        if (role == MemberRole.SUPER_ADMIN || role == MemberRole.ADMIN) {
            val badge = if (role == MemberRole.SUPER_ADMIN) "👑" else "🛡️"
            Text(
                text = badge,
                fontSize = (size.value * 0.35f).sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
            )
        }
    }
}

