package app.olauncher.fork.pager

import android.content.Context
import android.view.MotionEvent
import android.view.View
import app.olauncher.databinding.FragmentHomeBinding

/**
 * Decorates HomeFragment touch listeners so that when [androidx.viewpager2.widget.ViewPager2]
 * intercepts a horizontal drag and dispatches [MotionEvent.ACTION_CANCEL], pressed visual states
 * on Home text views are cleared without modifying upstream listener classes.
 */
object ForkHomeTouchDecorator {

    fun shouldClearPressedOnAction(actionMasked: Int): Boolean =
        actionMasked == MotionEvent.ACTION_CANCEL

    fun wrap(delegate: View.OnTouchListener): View.OnTouchListener =
        View.OnTouchListener { view, event ->
            if (shouldClearPressedOnAction(event.actionMasked)) {
                view.isPressed = false
            }
            delegate.onTouch(view, event)
        }

    fun decorate(
        binding: FragmentHomeBinding,
        context: Context,
        mainListenerFactory: (Context) -> View.OnTouchListener,
        itemListenerFactory: (Context, View) -> View.OnTouchListener,
    ) {
        binding.mainLayout.setOnTouchListener(wrap(mainListenerFactory(context)))
        val homeViews = listOf(
            binding.homeApp1,
            binding.homeApp2,
            binding.homeApp3,
            binding.homeApp4,
            binding.homeApp5,
            binding.homeApp6,
            binding.homeApp7,
            binding.homeApp8,
        )
        homeViews.forEach { appView ->
            appView.setOnTouchListener(wrap(itemListenerFactory(context, appView)))
        }
    }
}
