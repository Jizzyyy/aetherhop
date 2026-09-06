package com.kadhafi.aetherhop.presentation.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

object ChatTextFormatter {

    fun format(text: String): AnnotatedString {
        return buildAnnotatedString {
            var i = 0
            while (i < text.length) {
                if (text[i] == '*' && text.indexOf('*', i + 1) != -1) {
                    val nextStar = text.indexOf('*', i + 1)
                    val boldContent = text.substring(i + 1, nextStar)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldContent)
                    pop()
                    i = nextStar + 1
                } else if (text[i] == '_' && text.indexOf('_', i + 1) != -1) {
                    val nextUnder = text.indexOf('_', i + 1)
                    val italicContent = text.substring(i + 1, nextUnder)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicContent)
                    pop()
                    i = nextUnder + 1
                } else {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
