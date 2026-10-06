package com.wakaroute.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import com.wakaroute.app.data.Deck
import com.wakaroute.app.feature.cards.CardLicenseScreen
import com.wakaroute.app.feature.cards.CardStudyScreen
import com.wakaroute.app.feature.cards.CardsScreen
import com.wakaroute.app.feature.documents.DocumentScreen
import com.wakaroute.app.feature.goals.TargetSchoolsScreen
import com.wakaroute.app.feature.home.HomeScreen
import com.wakaroute.app.feature.learn.CourseScreen
import com.wakaroute.app.feature.learn.LearnScreen
import com.wakaroute.app.feature.learn.LessonScreen
import com.wakaroute.app.feature.learn.PathScreen
import com.wakaroute.app.feature.learn.SubjectScreen
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
import com.wakaroute.app.feature.tests.TestListScreen
import com.wakaroute.app.feature.tests.TestScreen
import com.wakaroute.core.documents.BundledDocument
import com.wakaroute.core.map.SchoolSubject
import kotlinx.coroutines.launch

/**
 * The five tabs of §5, with the same names and meanings as iOS — which is what
 * the 共通判断規則 requires: 「見た目は違っていい、言葉は揃える」.
 *
 * [owns] lists the route prefixes pushed from a tab, so the tab stays selected
 * while a student is inside it.
 */
private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val owns: List<String> = emptyList(),
) {
    Home(Routes.HOME, "ホーム", Icons.Filled.Home),
    Learn(
        Routes.LEARN,
        "学ぶ",
        Icons.AutoMirrored.Filled.MenuBook,
        owns = listOf("subjects", "paths", "courses", "lessons", "tests", "map", "cards", "card-licenses"),
    ),
    Record(Routes.RECORD, "記録", Icons.Filled.Timer),
    Schools(Routes.SCHOOLS, "高校を探す", Icons.Filled.School),
    More(Routes.MORE, "その他", Icons.Filled.MoreHoriz),
}

object Routes {
    const val INTRODUCTION = "introduction"
    const val HOME = "home"
    const val LEARN = "learn"
    const val SUBJECT = "subjects/{subject}"
    const val PATH = "paths/{pathId}?title={title}"
    const val COURSE = "courses/{courseId}?title={title}"
    const val MAP = "map"
    const val CARDS = "cards"
    const val CARD_DECK = "cards/{deck}"
    const val CARD_LICENSES = "card-licenses"
    const val RECORD = "record"
    const val SCHOOLS = "schools"
    const val MORE = "more"
    const val HANDOVER = "handover/{mode}"
    const val GOALS = "goals"
    const val LESSON = "lessons/{lessonId}"
    const val QUIZ = "lessons/{lessonId}/quiz"
    const val TESTS = "tests"
    const val TEST = "tests/{testId}"

    const val DOMAIN = "map/{subject}/{domain}"
    const val ELEMENT = "map/{subject}/{domain}/{element}"
    const val SCHOOL_DETAIL = "schools/{schoolId}"
    const val DOCUMENT = "documents/{document}"

    fun cardDeck(deck: Deck) = "cards/${deck.name}"

    fun subject(subject: SchoolSubject) = "subjects/${subject.name}"

    fun path(pathId: String, title: String) = "paths/${encode(pathId)}?title=${encode(title)}"

    fun course(courseId: String, title: String) = "courses/${encode(courseId)}?title=${encode(title)}"

    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    fun domain(subject: SchoolSubject, domainCode: String) = "map/${subject.name}/$domainCode"

    fun element(subject: SchoolSubject, domainCode: String, elementId: String) =
        "map/${subject.name}/$domainCode/${java.net.URLEncoder.encode(elementId, "UTF-8")}"

    fun schoolDetail(schoolId: String) = "schools/${java.net.URLEncoder.encode(schoolId, "UTF-8")}"

    fun lesson(lessonId: String) = "lessons/${java.net.URLEncoder.encode(lessonId, "UTF-8")}"

    fun quiz(lessonId: String) = "lessons/${java.net.URLEncoder.encode(lessonId, "UTF-8")}/quiz"

    fun test(testId: String) = "tests/${java.net.URLEncoder.encode(testId, "UTF-8")}"

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
                    val selected = currentRoute?.destination?.belongsTo(tab) == true

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
            onOpenLearn = { navController.switchTab(Routes.LEARN) },
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

    composable(Routes.LEARN) {
        LearnScreen(
            content = services.content,
            onOpenTests = { navController.navigate(Routes.TESTS) },
            onOpenCards = { navController.navigate(Routes.CARDS) },
            onOpenSubject = { navController.navigate(Routes.subject(it)) },
            onOpenMap = { navController.navigate(Routes.MAP) },
        )
    }

    composable(Routes.CARDS) {
        CardsScreen(
            cards = services.cards,
            onOpenDeck = { navController.navigate(Routes.cardDeck(it)) },
            onOpenLicenses = { navController.navigate(Routes.CARD_LICENSES) },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.CARD_DECK) { entry ->
        CardStudyScreen(
            cards = services.cards,
            deckName = entry.arguments?.getString("deck").orEmpty(),
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.CARD_LICENSES) {
        CardLicenseScreen(cards = services.cards, onBack = navController::popBackStack)
    }

    composable(Routes.SUBJECT) { entry ->
        SubjectScreen(
            content = services.content,
            subjectName = entry.arguments?.getString("subject").orEmpty(),
            onOpenPath = { id, title -> navController.navigate(Routes.path(id, title)) },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.PATH) { entry ->
        PathScreen(
            content = services.content,
            pathId = entry.arguments?.getString("pathId").orEmpty(),
            title = entry.arguments?.getString("title").orEmpty(),
            onOpenCourse = { id, title -> navController.navigate(Routes.course(id, title)) },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.COURSE) { entry ->
        CourseScreen(
            content = services.content,
            courseId = entry.arguments?.getString("courseId").orEmpty(),
            title = entry.arguments?.getString("title").orEmpty(),
            onOpenLesson = { navController.navigate(Routes.lesson(it)) },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.MAP) {
        UnderstandingMapScreen(
            mapState = services.understandingMap,
            onOpenDomain = { subject, domain -> navController.navigate(Routes.domain(subject, domain)) },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.TESTS) {
        TestListScreen(
            content = services.content,
            onOpenTest = { navController.navigate(Routes.test(it)) },
            onBack = navController::popBackStack,
        )
    }

    composable(Routes.TEST) { entry ->
        TestScreen(
            content = services.content,
            queue = services.actionQueue,
            testId = entry.arguments?.getString("testId").orEmpty(),
            onBack = navController::popBackStack,
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

/** True when this destination is [tab] itself or a screen pushed from it. */
private fun androidx.navigation.NavDestination.belongsTo(tab: Tab): Boolean {
    val first = route?.substringBefore('/')?.substringBefore('?') ?: return false
    return first == tab.route || first in tab.owns
}
