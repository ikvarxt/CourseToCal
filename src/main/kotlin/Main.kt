import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.TimeZoneRegistryImpl
import net.fortuna.ical4j.model.property.*
import net.fortuna.ical4j.util.RandomUidGenerator
import net.fortuna.ical4j.data.CalendarOutputter

import java.io.FileOutputStream
import java.util.GregorianCalendar
import java.time.LocalDateTime
import java.time.Duration


fun main() {
    // 0x01: 键入正确有效的教务在线账号密码
    val account = Account("your-student-ID", "your-password-for-JWZX")
    val lessons = getCoursesInfo(account)
    if (lessons.isEmpty()) return

    val calendar = generateICalendar(lessons)

    // generate ics file
    val fileOutputStream = FileOutputStream("${LocalDateTime.now()}-calendar.ics")
    CalendarOutputter().output(calendar, fileOutputStream)
}

// 0x02: 调整正确的学期开始日期
// 学期第一周周一日期
val firstMon: LocalDateTime = LocalDateTime.of(2021, 3, 8, 0, 0, 0)
val courseTimeSchedule = arrayOf("08:00", "09:55", "13:30", "15:25", "18:30")
const val COURSE_DURATION = 95

// 是否开启上课提醒，默认开启
const val ALARM = true

// 提醒通知提前时长
const val ALARM_TIME: Long = 15

data class Account(val studentID: String, val password: String)

data class CourseResponse(
    val code: String,
    val message: String,
    val data: List<Lesson>
)

data class Lesson(
    val code: String,
    val name: String,
    val teacher: String,
    val credit: String,
    val scheduleList: List<Schedule>
)

data class Schedule(
    val room: String,
    val weekday: Int,
    val index: Int,
    val weeksString: String,
    val weeks: List<Int>
)


fun getCoursesInfo(account: Account): List<Lesson> {
    val baseUrl = "https://api.liaoguoyin.com/"
    val coursePath = "education/course-table?type=class"
    val client = OkHttpClient()
    val gson = Gson()

    val bodyType = "application/json".toMediaType()
    val body = ("{\"username\": \"${account.studentID}\", \"password\": \"${account.password}\"}")
        .toRequestBody(bodyType)

    //TODO why this doesn't work
//    val body = FormBody.Builder()
//        .add("username", account.username)
//        .add("password", account.password)
//        .build()

    val request = Request.Builder()
        .url(baseUrl + coursePath)
        .post(body)
        .build()


    val response = client.newCall(request).execute()
    val requestData = response.body?.string()
    val courseResponse = gson.fromJson(requestData, CourseResponse::class.java)

    if (response.code == 400) {
        println(courseResponse.message)
        return emptyList()
    }

    return courseResponse.data
}

fun generateICalendar(lessons: List<Lesson>): Calendar {

    val icsCalendar = Calendar()
    icsCalendar.properties.add(ProdId("-//Events Calendar//iCal4j 1.0//EN"))
    icsCalendar.properties.add(CalScale.GREGORIAN)
    icsCalendar.properties.add(Version.VERSION_2_0)

    // 直接用实现类了，整什么 factory
    val timezone = TimeZoneRegistryImpl().getTimeZone("Asia/Shanghai")

    for (lesson in lessons) {
        for (schedule in lesson.scheduleList) {
            // course starts week number
            val weekNo = schedule.weeks[0]

            // calendar to calculate the start and end time of a lesson
            val startDateTime: java.util.Calendar = GregorianCalendar()
            startDateTime.timeZone = timezone
            // current course start at someday
            val st = firstMon
                .plusWeeks((weekNo - 1).toLong())
                .plusDays((schedule.weekday - 1).toLong())

            // current course start at specific time
            val startTimeStr = courseTimeSchedule[schedule.index - 1]
            startDateTime.set(
                st.year,
                st.monthValue - 1,
                st.dayOfMonth,
                startTimeStr.split(":")[0].toInt(),
                startTimeStr.split(":")[1].toInt(),
                0
            )
            val endDateTime = startDateTime.clone() as java.util.Calendar
            endDateTime.add(java.util.Calendar.MINUTE, COURSE_DURATION)

            // create a event with name and start and end time
            val course = VEvent(
                DateTime(startDateTime.time),
                DateTime(endDateTime.time),
                lesson.name
            )

            // generate until date for recurrence
            //TODO 如果周日有课，结束日期可能会有问题
            val untilTemp = firstMon
                .plusWeeks(schedule.weeks.last().toLong() - 1)
                .plusDays(6) // until this week end at sunday
            val untilDate: java.util.Calendar = GregorianCalendar().apply {
                timeZone = timezone
                set(untilTemp.year, untilTemp.monthValue - 1, untilTemp.dayOfMonth)
            }

            // generate a recurrence
            //TODO 单双周课程支持
            val recur = Recur.Builder()
                .frequency(Recur.Frequency.WEEKLY)
                .until(DateTime(untilDate.time))
                .interval(1)
                .build()
            val rRule = RRule(recur)

            val description = Description("教师：${lesson.teacher}，学分：${lesson.credit}")
            // split drop the room code like "EY103"
            val location = Location(schedule.room.split("(")[0])
            // reminder
            // 确实能用了！！
            if (ALARM) {
                val dur = Duration.ofMinutes(-ALARM_TIME)
                val reminder = VAlarm(dur)
                reminder.properties.apply {
                    add(Action("DISPLAY"))
                    add(Description("${lesson.name} 还有 $ALARM_TIME 分钟就要上课啦！"))
                }
                course.alarms.add(reminder)
            }

            // add properties to single course event
            course.properties.add(timezone.vTimeZone.timeZoneId)
            course.properties.add(RandomUidGenerator().generateUid())
            course.properties.add(rRule)
            course.properties.add(description)
            course.properties.add(location)

            icsCalendar.components.add(course)
        }
    }
    return icsCalendar
}
