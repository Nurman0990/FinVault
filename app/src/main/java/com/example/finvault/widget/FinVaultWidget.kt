package com.example.finvault.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.finvault.AppDatabase
import com.example.finvault.TransactionEntity
import kotlinx.coroutines.flow.first

class FinVaultWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val transactions = db.transactionDao().getAllTransactions().first()
        val totalBalance = transactions.sumOf { if (it.isIncome) it.amount else -it.amount }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1F22))
                        .cornerRadius(18.dp)
                        .padding(14.dp),
                    verticalAlignment = Alignment.Vertical.CenterVertically,
                    horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Vertical.CenterVertically,
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                    ) {
                        Text(
                            text = "FinVault",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF90CAF9)),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(6.dp))

                    Text(
                        text = com.example.finvault.util.CurrencyConverter.formatAmount(totalBalance, "KZT"),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(10.dp))

                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                    ) {
                        QuickAddButton(
                            label = "☕ Кофе 1500₸",
                            title = "Кофе",
                            amount = 1500
                        )
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        QuickAddButton(
                            label = "🍔 Обед 2500₸",
                            title = "Обед",
                            amount = 2500
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAddButton(label: String, title: String, amount: Int) {
    val titleParam = ActionParameters.Key<String>("title")
    val amountParam = ActionParameters.Key<Int>("amount")

    Box(
        modifier = GlanceModifier
            .background(Color(0xFF2B2D30))
            .cornerRadius(10.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(
                actionRunCallback<AddExpenseActionCallback>(
                    actionParametersOf(
                        titleParam to title,
                        amountParam to amount
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

class AddExpenseActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val title = parameters[ActionParameters.Key<String>("title")] ?: "Быстрая трата"
        val amount = parameters[ActionParameters.Key<Int>("amount")] ?: 1000

        val db = AppDatabase.getDatabase(context)
        db.transactionDao().insertTransaction(
            TransactionEntity(
                amount = amount.toDouble(),
                isIncome = false,
                title = title,
                timestamp = System.currentTimeMillis(),
                currencyCode = "KZT"
            )
        )

        FinVaultWidget().update(context, glanceId)
    }
}

class FinVaultWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FinVaultWidget()
}
