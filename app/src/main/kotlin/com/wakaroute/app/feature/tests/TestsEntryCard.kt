package com.wakaroute.app.feature.tests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wakaroute.app.ui.design.AdaptiveRow

/** The way into the スタート診断, which exist for all five 教科. */
@Composable
fun TestsEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        AdaptiveRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) { flexible ->
            Column(modifier = flexible, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "スタート診断", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "5教科それぞれ、中1から中3までのどこに穴があるかをテストで確かめます。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
