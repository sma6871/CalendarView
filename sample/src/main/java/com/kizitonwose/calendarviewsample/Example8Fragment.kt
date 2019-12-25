package com.kizitonwose.calendarviewsample


import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import com.google.android.material.snackbar.Snackbar
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.CalendarMonth
import com.kizitonwose.calendarview.model.DayOwner
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import com.kizitonwose.calendarview.utils.persian.*
import kotlinx.android.synthetic.main.calendar_day_legend.*
import kotlinx.android.synthetic.main.example_4_calendar_day.view.*
import kotlinx.android.synthetic.main.example_4_calendar_header.view.*
import kotlinx.android.synthetic.main.example_8_fragment.*
import org.threeten.bp.LocalDate
import org.threeten.bp.YearMonth
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

class Example8Fragment : BaseFragment(), HasToolbar, HasBackButton {

    override val toolbar: Toolbar?
        get() = exEightToolbar

    override val titleRes: Int? = null

    private val today = LocalDate.now()

    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null

    private val headerDateFormatter = DateTimeFormatter.ofPattern("EEE'\n'd MMM")

    private val startBackground: GradientDrawable by lazy {
        requireContext().getDrawableCompat(R.drawable.example_4_continuous_selected_bg_start) as GradientDrawable
    }

    private val endBackground: GradientDrawable by lazy {
        requireContext().getDrawableCompat(R.drawable.example_4_continuous_selected_bg_end) as GradientDrawable
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.example_8_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // We set the radius of the continuous selection background drawable dynamically
        // since the view size is `match parent` hence we cannot determine the appropriate
        // radius value which would equal half of the view's size beforehand.
        exEightCalendar.post {
            val radius = ((exEightCalendar.width / 7) / 2).toFloat()
            startBackground.setCornerRadius(topLeft = radius, bottomLeft = radius)
            endBackground.setCornerRadius(topRight = radius, bottomRight = radius)
        }

        // Set the First day of week of Persian Locale
        val daysOfWeek = daysOfWeekFromLocale(PersianCalendarConstants.getFaLocale())

        legendLayout.children.forEachIndexed { index, view ->
            (view as TextView).apply {
                text = daysOfWeek[index].getPersianDisplayFirstCharString()// getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColorRes(R.color.example_4_grey)
            }
        }

        val currentMonth = YearMonth.now()
        exEightCalendar.setup(currentMonth, currentMonth.plusMonths(12), daysOfWeek.first())
        exEightCalendar.scrollToMonth(currentMonth)

        class DayViewContainer(view: View) : ViewContainer(view) {
            lateinit var day: CalendarDay // Will be set when this container is bound.
            val textView = view.exFourDayText
            val roundBgView = view.exFourRoundBgView

            init {
                view.setOnClickListener {
                    if (day.owner == DayOwner.THIS_MONTH && (day.date == today || day.date.isAfter(today))) {
                        val date = day.date
                        if (startDate != null) {
                            if (date < startDate || endDate != null) {
                                startDate = date
                                endDate = null
                            } else if (date != startDate) {
                                endDate = date
                            }
                        } else {
                            startDate = date
                        }
                        exEightCalendar.notifyCalendarChanged()
                        bindSummaryViews()
                    }
                }
            }
        }
        exEightCalendar.dayBinder = object : DayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {

                container.day = day
                val textView = container.textView
                val roundBgView = container.roundBgView

                textView.text = null
                textView.background = null
                roundBgView.makeInVisible()

                if (day.owner == DayOwner.THIS_MONTH) {
                    textView.text = day.persianCalendar.persianDay.toString()

                    if (day.date.isBefore(today)) {
                        textView.setTextColorRes(R.color.example_4_grey_past)
                    } else {
                        when {
                            startDate == day.date && endDate == null -> {
                                textView.setTextColorRes(R.color.white)
                                roundBgView.makeVisible()
                                roundBgView.setBackgroundResource(R.drawable.example_4_single_selected_bg)
                            }
                            day.date == startDate -> {
                                textView.setTextColorRes(R.color.white)
                                textView.background = startBackground
                            }
                            startDate != null && endDate != null && (day.date > startDate && day.date < endDate) -> {
                                textView.setTextColorRes(R.color.white)
                                textView.setBackgroundResource(R.drawable.example_4_continuous_selected_bg_middle)
                            }
                            day.date == endDate -> {
                                textView.setTextColorRes(R.color.white)
                                textView.background = endBackground
                            }
                            day.date == today -> {
                                textView.setTextColorRes(R.color.example_4_grey)
                                roundBgView.makeVisible()
                                roundBgView.setBackgroundResource(R.drawable.example_4_today_bg)
                            }
                            else -> textView.setTextColorRes(R.color.example_4_grey)
                        }
                    }
                } else {

                    // This part is to make the coloured selection background continuous
                    // on the blank in and out dates across various months and also on dates(months)
                    // between the start and end dates if the selection spans across multiple months.

                    val startDate = startDate?.toPersianCalendar()
                    val endDate = endDate?.toPersianCalendar()
                    if (startDate != null && endDate != null) {
                        // Mimic selection of inDates that are less than the startDate.
                        // Example: When 26 Feb 2019 is startDate and 5 Mar 2019 is endDate,
                        // this makes the inDates in Mar 2019 for 24 & 25 Feb 2019 look selected.
                        if ((day.owner == DayOwner.PREVIOUS_MONTH
                                && startDate.persianMonth == day.persianCalendar.persianMonth
                                && endDate.persianMonth != day.persianCalendar.persianMonth) ||
                            // Mimic selection of outDates that are greater than the endDate.
                            // Example: When 25 Apr 2019 is startDate and 2 May 2019 is endDate,
                            // this makes the outDates in Apr 2019 for 3 & 4 May 2019 look selected.
                            (day.owner == DayOwner.NEXT_MONTH
                                && startDate.persianMonth != day.persianCalendar.persianMonth
                                && endDate.persianMonth == day.persianCalendar.persianMonth) ||

                            // Mimic selection of in and out dates of intermediate
                            // months if the selection spans across multiple months.
                            (startDate < day.persianCalendar && endDate > day.persianCalendar
                                && startDate.persianMonth != day.persianCalendar.persianMonth
                                && endDate.persianMonth != day.persianCalendar.persianMonth)
                        ) {
                            textView.setBackgroundResource(R.drawable.example_4_continuous_selected_bg_middle)
                        }
                    }
                }
            }
        }

        class MonthViewContainer(view: View) : ViewContainer(view) {
            val textView = view.exFourHeaderText
        }
        exEightCalendar.monthHeaderBinder = object : MonthHeaderFooterBinder<MonthViewContainer> {
            override fun create(view: View) = MonthViewContainer(view)
            override fun bind(container: MonthViewContainer, month: CalendarMonth) {
                val persianlCalendar = month.yearMonth.persianlCalendar()
                val monthTitle = "${persianlCalendar.persianMonthName} ${persianlCalendar.persianYear}"
                container.textView.text = monthTitle
            }
        }

        exEightSaveButton.setOnClickListener click@{
            val startDate = startDate
            val endDate = endDate
            if (startDate != null && endDate != null) {
                val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
                val text = "Selected: ${formatter.format(startDate)} - ${formatter.format(endDate)}"
                Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(requireView(), "No selection. Searching all Airbnb listings.", Snackbar.LENGTH_LONG)
                    .show()
            }
            fragmentManager?.popBackStack()
        }

        bindSummaryViews()
    }

    private fun bindSummaryViews() {
        if (startDate != null) {
            exEightStartDateText.text = headerDateFormatter.format(startDate)
            exEightStartDateText.setTextColorRes(R.color.example_4_grey)
        } else {
            exEightStartDateText.text = getString(R.string.start_date)
            exEightStartDateText.setTextColor(Color.GRAY)
        }
        if (endDate != null) {
            exEightEndDateText.text = headerDateFormatter.format(endDate)
            exEightEndDateText.setTextColorRes(R.color.example_4_grey)
        } else {
            exEightEndDateText.text = getString(R.string.end_date)
            exEightEndDateText.setTextColor(Color.GRAY)
        }

        // Enable save button if a range is selected or no date is selected at all, Airbnb style.
        exEightSaveButton.isEnabled = endDate != null || (startDate == null && endDate == null)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.example_4_menu, menu)
        exEightToolbar.post {
            // Configure menu text to match what is in the Airbnb app.
            exEightToolbar.findViewById<TextView>(R.id.menuItemClear).apply {
                setTextColor(requireContext().getColorCompat(R.color.example_4_grey))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                isAllCaps = false
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menuItemClear) {
            startDate = null
            endDate = null
            exEightCalendar.notifyCalendarChanged()
            bindSummaryViews()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        val closeIndicator = requireContext().getDrawableCompat(R.drawable.ic_close)?.apply {
            setColorFilter(requireContext().getColorCompat(R.color.example_4_grey), PorterDuff.Mode.SRC_ATOP)
        }
        (activity as AppCompatActivity).supportActionBar?.setHomeAsUpIndicator(closeIndicator)
        requireActivity().window.apply {
            // Update statusbar color to match toolbar color.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                statusBarColor = requireContext().getColorCompat(R.color.white)
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                statusBarColor = Color.GRAY
            }
        }
    }

    override fun onStop() {
        super.onStop()
        requireActivity().window.apply {
            // Reset statusbar color.
            statusBarColor = requireContext().getColorCompat(R.color.colorPrimaryDark)
            decorView.systemUiVisibility = 0
        }
    }
}
