package com.zionhuang.music.ui.fragments

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.zionhuang.music.R
import com.zionhuang.music.databinding.FragmentLibraryBinding
import java.util.*

class LibraryFragment : BindingFragment<FragmentLibraryBinding>() {
    companion object {
        private const val TAG = "LibraryFragment"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
    }

    private fun setupViewPager() {
        val libraryAdapter = LibraryAdapter(childFragmentManager).apply {
            addFragment(SongsFragment(), "All Songs")
        }
        binding.viewpager.apply {
            adapter = libraryAdapter
            offscreenPageLimit = 1
            currentItem = 0
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_search_icon, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return true
    }

    internal class LibraryAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        private val pages = ArrayList<Page>()
        fun addFragment(fragment: Fragment, title: String) {
            pages.add(Page(fragment, title))
        }

        override fun getItem(position: Int): Fragment = pages[position].fragment

        override fun getCount(): Int = pages.size

        override fun getPageTitle(position: Int): CharSequence = pages[position].title

        internal class Page(var fragment: Fragment, var title: String)
    }
}