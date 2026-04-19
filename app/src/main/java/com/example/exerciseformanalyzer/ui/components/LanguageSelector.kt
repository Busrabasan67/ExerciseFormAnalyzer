package com.example.exerciseformanalyzer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.appcompat.app.AppCompatDelegate

@Composable
fun LanguageSlidingToggle(
    modifier: Modifier = Modifier,
    onLanguageChange: (String) -> Unit
) {
    val currentLang = remember {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tags.contains("en")) "en" else "tr"
    }
    
    // Dillere göre index: TR = 0, EN = 1
    val selectedIndex = if (currentLang == "en") 1 else 0
    
    // Kayan pill (hap) arka planı için animasyonlu offset
    val itemWidth = 50.dp
    val targetOffset by animateDpAsState(
        targetValue = if (selectedIndex == 1) itemWidth else 0.dp,
        animationSpec = spring(stiffness = 500f),
        label = "pillOffset"
    )

    Box(
        modifier = modifier
            .width(itemWidth * 2)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(2.dp)
    ) {
        // Kayan Arka Plan (Pill)
        Box(
            modifier = Modifier
                .offset(x = targetOffset)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        // Yazılar (TR ve EN)
        Row(modifier = Modifier.fillMaxSize()) {
            LanguageItem(
                text = "TR",
                isSelected = selectedIndex == 0,
                modifier = Modifier.weight(1f),
                onClick = { if (selectedIndex != 0) onLanguageChange("tr") }
            )
            LanguageItem(
                text = "EN",
                isSelected = selectedIndex == 1,
                modifier = Modifier.weight(1f),
                onClick = { if (selectedIndex != 1) onLanguageChange("en") }
            )
        }
    }
}

@Composable
private fun LanguageItem(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "textColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}
