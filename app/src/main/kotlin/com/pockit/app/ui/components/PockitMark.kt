package com.pockit.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockit.app.R
import com.pockit.app.ui.theme.White

@Composable
fun PockitWordmark(modifier: Modifier = Modifier, fontSize: Int = 20) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(R.drawable.pockit_mark),
            contentDescription = null,
            modifier = Modifier.size((fontSize * 1.1f).dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Pockit",
            color = White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
