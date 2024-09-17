package com.elgenium.smartcity.expandable_list_adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.TextView
import com.elgenium.smartcity.R

class FAQExpandableListAdapter(
    private val context: Context,
    private val faqList: Map<String, List<String>>,
    private val faqHeaders: List<String>
) : BaseExpandableListAdapter() {

    override fun getGroupCount(): Int = faqHeaders.size

    override fun getChildrenCount(groupPosition: Int): Int = faqList[faqHeaders[groupPosition]]!!.size

    override fun getGroup(groupPosition: Int): String = faqHeaders[groupPosition]

    override fun getChild(groupPosition: Int, childPosition: Int): String = faqList[faqHeaders[groupPosition]]!![childPosition]

    override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

    override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()

    override fun hasStableIds(): Boolean = false

    override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup?): View {
        val headerTitle = getGroup(groupPosition)
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.faq_group_item, parent, false)

        val groupTextView = view.findViewById<TextView>(R.id.faq_group_text)
        groupTextView.text = headerTitle

        return view
    }

    override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup?): View {
        val childText = getChild(groupPosition, childPosition)
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.faq_child_item, parent, false)

        val childTextView = view.findViewById<TextView>(R.id.faq_child_text)
        childTextView.text = childText

        return view
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true
}
