package com.wakaroute.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wakaroute.app.AppServices
import com.wakaroute.app.feature.documents.DocumentScreen
import com.wakaroute.app.feature.goals.TargetSchoolsScreen
import com.wakaroute.app.feature.home.HomeScreen
import com.wakaroute.app.feature.learn.LessonScreen
import com.wakaroute.app.feature.learn.QuizScreen
import com.wakaroute.app.feature.map.DomainScreen
import com.wakaroute.app.feature.map.ElementScreen
import com.wakaroute.app.feature.map.UnderstandingMapScreen
import com.wakaroute.app.feature.more.HandoverMode
import com.wakaroute.app.feature.more.HandoverScreen
import com.wakaroute.app.feature.more.MoreScreen
import com.wakaroute.app.feature.onboarding.IntroductionScreen
import com.wakaroute.app.feature.schools.SchoolDetailScreen
import com.wakaroute.app.feature.schools.SchoolSearchScreen
import com.wakaroute.app.feature.study.StudyRecordScreen
import com.wakaroute.core.documents.BundledDocument
import com.wakaroute.core.map.SchoolSubject
import kotlinx.coroutines.launch

/**
 * Four tabs, not the five of §5.
 *
 * 学ぶ and 記録 belong to Phase 2 and have nothing behind them yet. A tab that
 * opens onto 準備中 every time still teaches a student to stop tapping it, and
 * §4 is explicit that Phase 1 must not present unbuilt features as though they
 * were there. The names and meanings of the four that do exist match iOS, which
 * is what the 共通判断規則 actually requires — 「見た目は違っていい」.
 */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home(Routes.HOME, "ホーム", Icons.Filled.Home),
    Map(Routes.MAP, "理解マップ", Icons.Outlined.AccountTree),
    Record(Routes.RECORD, "記録", Icons.Filled.Timer),
    Schools(Routes.SCHOOLS, "高校を探す", Icons.Filled.School),
    More(Routes.MORE, "その他", Icons.Filled.MoreHoriz),
}

object Routes {
    const val INTRODUCTION = "introduction"
    const val HOME = "home"
    const val MAP = "map"
    const val RECORD = "record"
    const val SCHOOLS = "schools"
    const val MORE = "more"
    const val HANDOVER = "handover/{mode}"
    const val GOALS = "goals"
    const val LESSON = "lessons/{lessonId}"
    const val QUIZ = "lessons/{lessonId}/quiz"

    const val DOMAIN = "map/{subject}/{domain}"
    const val ELEMENT = "map/{subject}/{domain}/{element}"
    const val SCHOOL_DETAIL = "schools/{schoolId}"
    const val DOCUMENT = "documents/{document}"

    fun domain(subject: SchoolSubject, domainCode: String) = "map/${subject.name}/$domainCode"

    fun element(subject: SchoolSubject, domainCode: String, elementId: String) =
        "map/${subject.name}/$domainCode/${java.net.URLEncoder.encode(elementId, "UTF-8")}"

    fun schoolDetail(schoolId: String) = "schools/${java.net.URLEncoder.encode(schoolId, "UTF-8")}"

    fun lesson(lessonId: String) = "lessons/${java.net.URLEncoder.encode(lessonId, "UTF-8")}"

    fun quiz(lessonId: String) = "lessons/${java.net.URLEncoder.encode(lessonId, "UTF-8")}/quiz"

    fun document(document: BundledDocument) = "documents/${document.name}"

    fun handover(mode: HandoverMode) = "handover/${mode.name}"
}

@Composable
fun RootScreen(services: AppServices) {
    val hasSeenIntroduction by services.preferences.hasSeenIntroduction
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    when (hasSeenIntroduction) {
        // Still reading the flag. Deliberately blank rather than a spinner: the
        // read takes a frame or two, and a spinner that flashes reads as a fault.
        null -> Box(Modifier.fillMaxSize())

        false -> IntroductionScreen(
            onFinish = { scope.launch { services.preferences.markIntroductionSeen() } },
        )

        true -> MainScaffold(services)
    }
}

@Composable
private fun MainScaffold(services: AppServices) {
    val navController = rememberNavController()
    val currentRoute by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (tab in Tab.entries) {
                    val selected = currentRoute?.destination?.hierarchy(tab.route) == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.switchTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                        // The label is always visible. Icon-only tabs need the
                        // student to already know what the icons mean, and
                        // 理解マップ has no conventional icon to lean on.
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            appGraph(services, navController)
        }
    }
}

private fun NavGraphBuilder.appGraph(services: AppServices, navController: NavHostController) {
    composable(Routes.HOME) {
        HomeScreen(
            services = services,
            onOpenMap = { navController.switchTab(Routes.MAP) },
            onOpenSchools = { navController.switchTab(Routes.SCHOOLS) },
            onOpenGoals = { navController.navigate(Routes.GOALS) },
            onOpenElement = { subject, domain, element ->
                navController.navigate(Routes.element(subject, domain, element))
            },
            onOpenDocument = { navController.navigate(Routes.document(it)) },
        )
    }

    composable(Routes.GOALS) {
        TargetSchoolsScreen(
            targetSchools = services.targetSchools,
            onFindSchools = { navController.switchTab(Routes.SCHOOLS) },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.MAP) {
        UnderstandingMapScreen(
            mapState = services.understandingMap,
            onOpenDomain = { subject, domain -> navController.navigate(Routes.domain(subject, domain)) },
        )
    }

    composable(Routes.DOMAIN) { entry ->
        DomainScreen(
            mapState = services.understandingMap,
            subjectName = entry.arguments?.getString("subject").orEmpty(),
            domainCode = entry.arguments?.getString("domain").orEmpty(),
            onOpenElement = { subject, domain, element ->
                navController.navigate(Routes.element(subject, domain, element))
            },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.ELEMENT) { entry ->
        ElementScreen(
            mapState = services.understandingMap,
            content = services.content,
            onOpenLesson = { navController.navigate(Routes.lesson(it)) },
            subjectName = entry.arguments?.getString("subject").orEmpty(),
            domainCode = entry.arguments?.getString("domain").orEmpty(),
            elementId = entry.arguments?.getString("element").orEmpty(),
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.LESSON) { entry ->
        LessonScreen(
            content = services.content,
            queue = services.actionQueue,
            lessonId = entry.arguments?.getString("lessonId").orEmpty(),
            onOpenQuiz = { navController.navigate(Routes.quiz(it)) },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.QUIZ) { entry ->
        QuizScreen(
            content = services.content,
            queue = services.actionQueue,
            lessonId = entry.arguments?.getString("lessonId").orEmpty(),
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.RECORD) {
        StudyRecordScreen(
            timer = services.studyTimer,
            queue = services.actionQueue,
            content = services.content,
        )
    }

    composable(Routes.SCHOOLS) {
        SchoolSearchScreen(
            services = services,
            onOpenSchool = { navController.navigate(Routes.schoolDetail(it)) },
        )
    }

    composable(Routes.SCHOOL_DETAIL) { entry ->
        SchoolDetailScreen(
            schools = services.schools,
            targetSchools = services.targetSchools,
            schoolId = entry.arguments?.getString("schoolId").orEmpty(),
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.MORE) {
        MoreScreen(
            environment = services.environment,
            auth = services.auth,
            profile = services.profile,
            deletion = services.accountDeletion,
            onOpenDocument = { navController.navigate(Routes.document(it)) },
            onLink = { navController.navigate(Routes.handover(HandoverMode.Link)) },
            onSignIn = { navController.navigate(Routes.handover(HandoverMode.SignIn)) },
        )
    }

    composable(Routes.HANDOVER) { entry ->
        HandoverScreen(
            handover = services.handover,
            mode = HandoverMode.valueOf(entry.arguments?.getString("mode").orEmpty()),
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.DOCUMENT) { entry ->
        DocumentScreen(
            document = BundledDocument.valueOf(entry.arguments?.getString("document").orEmpty()),
            onBack = navController::popBackStack,
        )
    }
}

/**
 * Switches tabs without stacking them.
 *
 * Without `launchSingleTop` and the pop, tapping 高校を探す three times leaves
 * three copies on the back stack and the system back gesture walks through all
 * of them.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** True when [route] is this destination or one pushed from it. */
private fun androidx.navigation.NavDestination.hierarchy(route: String): Boolean =
    this.route == route || this.route?.startsWith("${route.substringBefore('/')}/") == true
