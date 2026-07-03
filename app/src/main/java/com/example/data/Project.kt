package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String,
    val codeKotlin: String,
    val codeLayout: String,
    val configJson: String,
    val accentColor: String,
    val iconName: String,
    val createdAt: Long = System.currentTimeMillis()
)
