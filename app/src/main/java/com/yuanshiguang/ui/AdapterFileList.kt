package com.yuanshiguang.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yuanshiguang.nekoscene.R
import com.yuanshiguang.utils.FileOpUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class AdapterFileList(
    private val onItemClick: (File) -> Unit,
    private val onItemLongClick: (File) -> Unit
) : RecyclerView.Adapter<AdapterFileList.ViewHolder>() {

    companion object {
        const val SORT_NAME = 0
        const val SORT_SIZE = 1
        const val SORT_TIME = 2
        const val SORT_TYPE = 3
    }

    private var items: MutableList<File> = ArrayList()
    private var hasParent = false
    private var parentFile: File? = null
    private val selected = HashSet<File>()
    var sortMode = SORT_NAME
        set(value) {
            field = value
            sortItems()
            notifyDataSetChanged()
        }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun setData(dir: File, files: Array<File>?, parentAvailable: Boolean) {
        parentFile = dir.parentFile
        hasParent = parentAvailable && parentFile != null && parentFile!!.canRead()
        items = ArrayList((files ?: emptyArray()).toList())
        sortItems()
        notifyDataSetChanged()
    }

    private fun sortItems() {
        val comparator = Comparator<File> { a, b ->
            // 文件夹始终在前
            if (a.isDirectory != b.isDirectory) return@Comparator if (a.isDirectory) -1 else 1
            when (sortMode) {
                SORT_SIZE -> b.length().compareTo(a.length())
                SORT_TIME -> b.lastModified().compareTo(a.lastModified())
                SORT_TYPE -> a.extension.toLowerCase().compareTo(b.extension.toLowerCase())
                else -> a.name.toLowerCase().compareTo(b.name.toLowerCase())
            }
        }
        items.sortWith(comparator)
    }

    fun isSelected(f: File): Boolean = selected.contains(f)
    fun selectionCount(): Int = selected.size
    fun getSelected(): List<File> = ArrayList(selected)
    fun clearSelection() {
        selected.clear()
        notifyDataSetChanged()
    }

    fun toggleSelection(f: File) {
        if (selected.contains(f)) selected.remove(f) else selected.add(f)
        notifyDataSetChanged()
    }

    fun selectAll() {
        for (f in items) selected.add(f)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size + if (hasParent) 1 else 0

    private fun fileAt(position: Int): File? {
        return if (hasParent) {
            if (position == 0) parentFile else items[position - 1]
        } else {
            items[position]
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (hasParent && position == 0) return 0
        val f = items[if (hasParent) position - 1 else position]
        return if (f.isDirectory) 1 else 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            0 -> R.layout.list_item_dir
            1 -> R.layout.list_item_dir
            else -> R.layout.list_item_file
        }
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isParent = hasParent && position == 0
        val file = if (isParent) parentFile else fileAt(position)

        if (isParent) {
            holder.title.text = ".."
            holder.subtitle.text = ""
            holder.icon.setImageResource(R.drawable.folder)
            holder.itemView.setBackgroundColor(0)
            holder.itemView.setOnClickListener { parentFile?.let { onItemClick(it) } }
            holder.itemView.setOnLongClickListener(null)
            return
        }

        file ?: return
        holder.title.text = file.name
        holder.subtitle.visibility = View.VISIBLE
        if (file.isDirectory) {
            val count = file.listFiles()?.size ?: 0
            holder.subtitle.text = holder.itemView.context.getString(R.string.fs_child_count, count)
            holder.icon.setImageResource(R.drawable.folder)
        } else {
            holder.subtitle.text = FileOpUtils.formatSize(file.length()) + "  " +
                    dateFmt.format(file.lastModified())
            holder.icon.setImageResource(R.drawable.file)
        }

        val sel = selected.contains(file)
        holder.itemView.setBackgroundColor(if (sel) 0x336C8FE8.toInt() else 0)
        holder.itemView.setOnClickListener { onItemClick(file) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(file)
            true
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ItemIcon)
        val title: TextView = view.findViewById(R.id.ItemTitle)
        val subtitle: TextView = view.findViewById(R.id.ItemText)
    }
}
