/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.zimmob.zimlx.preferences

import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.zimmob.zimlx.ZimPreferences

class IconPackFragment : RecyclerViewFragment() {

    private val adapter by lazy { IconPackAdapter(requireContext()) }

    override fun onRecyclerViewCreated(recyclerView: RecyclerView) {
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        adapter.itemTouchHelper = ItemTouchHelper(adapter.TouchHelperCallback()).apply {
            attachToRecyclerView(recyclerView)
        }
    }

    override fun onPause() {
        super.onPause()

        ZimPreferences.getInstance(requireContext()).iconPacks.setAll(adapter.saveSpecs())
    }
}
