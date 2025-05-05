package com.invenkode.cathedralcafeinventorylog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class RecipeRow {
    data class SectionHeader(val title: String) : RecipeRow()
    data class RecipeItem(val recipe: Recipe) : RecipeRow()
}

class RecipeAdapter(private val rows: List<RecipeRow>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RECIPE = 1
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerText: TextView = view.findViewById(R.id.tvSectionHeader)
    }

    inner class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val btnName: Button = view.findViewById(R.id.btnRecipeName)
        val stepsLayout: LinearLayout = view.findViewById(R.id.stepsLayout)
    }

    override fun getItemViewType(position: Int): Int {
        return when (rows[position]) {
            is RecipeRow.SectionHeader -> TYPE_HEADER
            is RecipeRow.RecipeItem -> TYPE_RECIPE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe, parent, false)
            RecipeViewHolder(view)
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is RecipeRow.SectionHeader -> {
                (holder as HeaderViewHolder).headerText.text = row.title
            }
            is RecipeRow.RecipeItem -> {
                val recipeHolder = holder as RecipeViewHolder
                recipeHolder.btnName.text = row.recipe.name
                recipeHolder.stepsLayout.removeAllViews()
                row.recipe.steps.forEach { step ->
                    val stepView = TextView(holder.itemView.context).apply {
                        text = "• $step"
                        setPadding(16, 4, 16, 4)
                        setTextColor(resources.getColor(R.color.black, null))
                    }
                    recipeHolder.stepsLayout.addView(stepView)
                }
                recipeHolder.stepsLayout.visibility = View.GONE

                recipeHolder.btnName.setOnClickListener {
                    val isVisible = recipeHolder.stepsLayout.visibility == View.VISIBLE
                    val anim = AlphaAnimation(if (isVisible) 1f else 0f, if (isVisible) 0f else 1f).apply {
                        duration = 300
                    }
                    recipeHolder.stepsLayout.startAnimation(anim)
                    recipeHolder.stepsLayout.visibility = if (isVisible) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
