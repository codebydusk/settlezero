package app.settlezero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.settlezero.core.ui.theme.SettleZeroTheme
import app.settlezero.feature.claim.ClaimViewModel
import app.settlezero.feature.claim.JoinGroupScreen
import app.settlezero.feature.expense.AddExpenseScreen
import app.settlezero.feature.expense.ExpenseViewModel
import app.settlezero.feature.group.GroupDashboardScreen
import app.settlezero.feature.group.GroupDashboardViewModel
import app.settlezero.feature.group.GroupDetailScreen
import app.settlezero.feature.group.GroupDetailViewModel
import app.settlezero.feature.settlement.SettleUpScreen
import app.settlezero.feature.settlement.SettlementViewModel

@Suppress("UNCHECKED_CAST")
class AppViewModelFactory<VM : ViewModel>(
    private val creator: () -> VM
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as SettleZeroApplication).container

        setContent {
            SettleZeroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettleZeroAppNavigation(appContainer = appContainer)
                }
            }
        }
    }
}

@Composable
fun SettleZeroAppNavigation(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard",
        modifier = modifier
    ) {
        composable("dashboard") {
            val dashboardViewModel: GroupDashboardViewModel = viewModel(
                factory = AppViewModelFactory {
                    GroupDashboardViewModel(
                        groupRepository = appContainer.groupRepository,
                        memberRepository = appContainer.memberRepository,
                        ledgerRepository = appContainer.ledgerRepository
                    )
                }
            )

            GroupDashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToGroup = { groupId ->
                    navController.navigate("group/$groupId")
                },
                onNavigateToJoin = {
                    navController.navigate("join")
                }
            )
        }

        composable(
            route = "group/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            val detailViewModel: GroupDetailViewModel = viewModel(
                key = "group_detail_$groupId",
                factory = AppViewModelFactory {
                    GroupDetailViewModel(
                        groupId = groupId,
                        groupRepository = appContainer.groupRepository,
                        memberRepository = appContainer.memberRepository,
                        expenseRepository = appContainer.expenseRepository,
                        ledgerRepository = appContainer.ledgerRepository
                    )
                }
            )

            GroupDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddExpense = { id ->
                    navController.navigate("expense/add/$id")
                },
                onNavigateToSettleUp = { id ->
                    navController.navigate("settle/$id")
                }
            )
        }

        composable(
            route = "expense/add/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            val expenseViewModel: ExpenseViewModel = viewModel(
                key = "expense_add_$groupId",
                factory = AppViewModelFactory {
                    ExpenseViewModel(
                        groupId = groupId,
                        groupRepository = appContainer.groupRepository,
                        memberRepository = appContainer.memberRepository,
                        expenseRepository = appContainer.expenseRepository
                    )
                }
            )

            AddExpenseScreen(
                viewModel = expenseViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "settle/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            val settlementViewModel: SettlementViewModel = viewModel(
                key = "settle_$groupId",
                factory = AppViewModelFactory {
                    SettlementViewModel(
                        groupId = groupId,
                        groupRepository = appContainer.groupRepository,
                        memberRepository = appContainer.memberRepository,
                        settlementRepository = appContainer.settlementRepository,
                        ledgerRepository = appContainer.ledgerRepository
                    )
                }
            )

            SettleUpScreen(
                viewModel = settlementViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "join?token={token}",
            arguments = listOf(navArgument("token") {
                type = NavType.StringType
                defaultValue = ""
            })
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token") ?: ""
            val claimViewModel: ClaimViewModel = viewModel(
                key = "claim_$token",
                factory = AppViewModelFactory {
                    ClaimViewModel(
                        initialToken = token,
                        groupRepository = appContainer.groupRepository,
                        memberRepository = appContainer.memberRepository
                    )
                }
            )

            JoinGroupScreen(
                viewModel = claimViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGroup = { groupId ->
                    navController.navigate("group/$groupId") {
                        popUpTo("dashboard")
                    }
                }
            )
        }
    }
}
