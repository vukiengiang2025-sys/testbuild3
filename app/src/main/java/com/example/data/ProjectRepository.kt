package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    val allProjects: Flow<List<Project>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: Long): Project? = projectDao.getProjectById(id)

    suspend fun insert(project: Project): Long = projectDao.insertProject(project)

    suspend fun update(project: Project) = projectDao.updateProject(project)

    suspend fun delete(project: Project) = projectDao.deleteProject(project)
}
