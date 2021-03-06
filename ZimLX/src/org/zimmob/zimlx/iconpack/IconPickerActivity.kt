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

package org.zimmob.zimlx.iconpack

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.os.Process
import android.text.TextUtils
import android.view.*
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.launcher3.LauncherModel
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.compat.LauncherAppsCompat
import org.zimmob.zimlx.*
import org.zimmob.zimlx.iconpack.EditIconActivity.Companion.EXTRA_ENTRY
import org.zimmob.zimlx.settings.ui.SettingsBaseActivity
import java.text.Collator
import java.util.*
import java.util.concurrent.Semaphore

class IconPickerActivity : SettingsBaseActivity(), View.OnLayoutChangeListener, SearchView.OnQueryTextListener, View.OnFocusChangeListener {
    private val iconPackManager = IconPackManager.getInstance(this)
    private val iconGrid by lazy { findViewById<RecyclerView>(R.id.iconGrid) }
    private val iconPack by lazy { iconPackManager.getIconPack(intent.getStringExtra(EXTRA_ICON_PACK), false) }
    private val items get() = searchItems ?: actualItems
    private var actualItems = ArrayList<AdapterItem>()
    private val adapter = IconGridAdapter()
    private val layoutManager = GridLayoutManager(this, 1)
    private var canceled = false
    private var searchView: SearchView? = null
    private val collator = Collator.getInstance()
    private var closingSearch: Boolean = false
    private val showDebugInfo = zimPrefs.showDebugInfo

    private var dynamicPadding = 0

    private val pickerComponent by lazy {
        LauncherAppsCompat.getInstance(this)
                .getActivityList(iconPack.packPackageName, Process.myUserHandle()).firstOrNull()?.componentName
    }

    private var searchItems: MutableList<AdapterItem>? = null
    private val searchHandler = object : Handler(LauncherModel.getIconPackUiLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == R.id.message_search) {
                processSearchQuery(msg.obj as String?)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_icon_picker)

        title = iconPack.displayName

        getContentFrame().addOnLayoutChangeListener(this)

        supportActionBar?.run {
            setDisplayShowHomeEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        items.add(LoadingItem())

        runOnUiWorkerThread {
            // make sure whatever running on ui worker has finished, then start parsing the pack
            runOnThread(iconPackUiHandler) {
                iconPack.getAllIcons(::addEntries, { canceled })
                // Wait for the ui to finish processing new data
                val waiter = Semaphore(0)
                runOnUiThread {
                    waiter.release()
                }
                waiter.acquireUninterruptibly()
                waiter.release()
            }
        }
        collator.apply {
            decomposition = Collator.CANONICAL_DECOMPOSITION
            strength = Collator.TERTIARY
        }
    }

    override fun finish() {
        super.finish()
        canceled = true
    }

    private fun addEntries(entries: List<IconPack.PackEntry>) {
        val newItems = entries.mapNotNull {
            when (it) {
                is IconPack.CategoryTitle -> CategoryItem(it.title)
                is IconPack.Entry -> IconItem(it)
                else -> null
            }
        }
        runOnUiThread {
            if (items.size == 1 && items[0] is LoadingItem) {
                items.removeAt(0)
                adapter.notifyItemRemoved(0)
            }

            val addIndex = items.size
            items.addAll(newItems)
            adapter.notifyItemRangeInserted(addIndex, newItems.size)
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        closingSearch = true
        searchView!!.apply {
            setQuery("", false)
            clearFocus()
        }
        return true
    }

    override fun onQueryTextChange(query: String?): Boolean {
        if (closingSearch) {
            closingSearch = false
            return true
        }
        addToSearchQueue(query)
        return true
    }

    private fun addToSearchQueue(query: String?) {
        searchHandler.removeMessages(R.id.message_search)
        searchHandler.sendMessage(searchHandler.obtainMessage(R.id.message_search, query))
    }

    private fun processSearchQuery(query: String?) {
        val q = query?.trim()
        val filtered = if (!TextUtils.isEmpty(q)) {
            actualItems.filter { it is IconItem && collator.equals(q!!, it.entry.displayName) }.toMutableList()
        } else null
        runOnUiThread {
            val hashCode = items.hashCode()
            searchItems = filtered
            if (items.hashCode() != hashCode) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_icon_picker, menu)
        if (pickerComponent == null) {
            menu.removeItem(R.id.action_open_external)
        }
        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView!!.setOnQueryTextListener(this)
        searchView!!.setOnQueryTextFocusChangeListener(this)
        // Unfortunately this is seriously the only way of changing this darn icon that worked for me.
        val searchIcon = searchView!!.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
        searchIcon.setImageDrawable(resources.getDrawable(R.drawable.ic_search, null).apply {
            setTint(Utilities.getZimPrefs(applicationContext).accentColor)
        })
        return true
    }

    override fun onFocusChange(view: View?, hasFocus: Boolean) {
        if (view == searchView) {
            if (hasFocus) {
                title = ""
            } else {
                searchView!!.isIconified = true
                title = iconPack.displayName
            }
        }
    }

    override fun onBackPressed() {
        if (searchView!!.isIconified) {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_open_external -> {
                val intent = Intent("com.novalauncher.THEME")
                        .addCategory("com.novalauncher.category.CUSTOM_ICON_PICKER")
                        .setComponent(pickerComponent)
                startActivityForResult(intent, 1000)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1000 && resultCode == Activity.RESULT_OK && data != null) {
            if (data.hasExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)) {
                val icon = data.getParcelableExtra<Intent.ShortcutIconResource>(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
                val entry = (iconPack as IconPackImpl).createEntry(icon)
                onSelectIcon(entry)
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        super.onDestroy()
        canceled = true
    }

    override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        getContentFrame().removeOnLayoutChangeListener(this)
        calculateDynamicGrid(iconGrid.width)
        iconGrid.adapter = adapter
        iconGrid.layoutManager = layoutManager
    }

    private fun calculateDynamicGrid(width: Int) {
        val iconPadding = resources.getDimensionPixelSize(R.dimen.icon_preview_padding)
        val iconSize = resources.getDimensionPixelSize(R.dimen.icon_preview_size)
        val iconSizeWithPadding = iconSize + iconPadding + iconPadding
        val maxWidth = width - iconPadding - iconPadding
        val columnCount = maxWidth / iconSizeWithPadding
        val usedWidth = iconSize * columnCount
        dynamicPadding = (width - usedWidth) / (columnCount + 1) / 2
        layoutManager.spanCount = columnCount
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = getItemSpan(position)
        }
        iconGrid.setPadding(dynamicPadding, iconPadding, dynamicPadding, iconPadding)
    }

    private fun getItemSpan(position: Int) = if (adapter.isItem(position)) 1 else layoutManager.spanCount

    fun onSelectIcon(entry: IconPack.Entry) {
        val customEntry = entry.toCustomEntry()
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ENTRY, customEntry.toString()))
        finish()
    }

    inner class IconGridAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val loadingType = 0
        private val itemType = 1
        private val categoryType = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                itemType -> IconHolder(layoutInflater.inflate(R.layout.icon_item, parent, false))
                categoryType -> CategoryHolder(layoutInflater.inflate(R.layout.icon_category, parent, false))
                else -> LoadingHolder(layoutInflater.inflate(R.layout.adapter_loading, parent, false))
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is IconHolder) {
                holder.bind(items[position] as IconItem)
            } else if (holder is CategoryHolder) {
                holder.bind(items[position] as CategoryItem)
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when {
                items[position] is IconItem -> itemType
                items[position] is CategoryItem -> categoryType
                else -> loadingType
            }
        }

        fun isItem(position: Int) = getItemViewType(position) == itemType

        inner class IconHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener, IconItem.Callback {

            private var iconLoader: IconItem? = null
                set(value) {
                    field?.callback = null
                    value?.callback = this
                    field = value
                }
            private var name = "Unknown"

            init {
                itemView.setOnClickListener(this)
                (itemView.layoutParams as ViewGroup.MarginLayoutParams).apply {
                    leftMargin = dynamicPadding
                    rightMargin = dynamicPadding
                }
                if (showDebugInfo) {
                    itemView.setOnLongClickListener {
                        Toast.makeText(applicationContext, name, Toast.LENGTH_LONG).show()
                        true
                    }
                }
            }

            fun bind(item: IconItem) {
                iconLoader = item
                iconLoader?.loadIcon()
                itemView.clearAnimation()
                itemView.alpha = 0f
                (itemView as ImageView).setImageDrawable(null)
                // We're just being optimistic here, but starting the animation in the callback
                // results in empty view holders in some cases and places.
                itemView.animate().alpha(1f).setDuration(125).start()
            }

            override fun onIconLoaded(drawable: Drawable, name: String) {
                (itemView as ImageView).setImageDrawable(drawable)
                this.name = name
            }

            override fun onClick(v: View) {
                onSelectIcon((items[adapterPosition] as IconItem).entry)
            }
        }

        inner class CategoryHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            private val title: TextView = itemView.findViewById(android.R.id.title)

            fun bind(category: CategoryItem) {
                title.text = category.title
            }
        }

        inner class LoadingHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }

    open class AdapterItem

    class CategoryItem(val title: String) : AdapterItem()

    class IconItem(val entry: IconPack.Entry) : AdapterItem() {

        var callback: Callback? = null

        fun loadIcon() {
            runOnUiWorkerThread {
                val drawable = entry.drawable
                val displayName = entry.displayName
                runOnMainThread { callback?.onIconLoaded(drawable, displayName) }
            }
        }

        interface Callback {

            fun onIconLoaded(drawable: Drawable, name: String)
        }
    }

    class LoadingItem : AdapterItem()

    companion object {

        private const val EXTRA_ICON_PACK = "pack"

        fun newIntent(context: Context, packageName: String): Intent {
            return Intent(context, IconPickerActivity::class.java).apply {
                putExtra(EXTRA_ICON_PACK, packageName)
            }
        }
    }
}
