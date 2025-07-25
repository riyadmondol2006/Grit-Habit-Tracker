package com.shub39.grit.tasks.data.repository

import com.shub39.grit.core.data.toCategory
import com.shub39.grit.core.data.toCategoryEntity
import com.shub39.grit.core.data.toTask
import com.shub39.grit.core.data.toTaskEntity
import com.shub39.grit.tasks.data.TaskNotificationScheduler
import com.shub39.grit.tasks.data.database.CategoryDao
import com.shub39.grit.tasks.data.database.TasksDao
import com.shub39.grit.tasks.domain.Category
import com.shub39.grit.tasks.domain.Task
import com.shub39.grit.tasks.domain.TaskRepo
import com.shub39.grit.widgets.TodoListWidgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class TasksRepository(
    private val tasksDao: TasksDao,
    private val categoryDao: CategoryDao
): TaskRepo, KoinComponent {
    
    private val widgetRepo: TodoListWidgetRepository by lazy { get() }
    private val notificationScheduler: TaskNotificationScheduler by lazy { get() }
    override fun getTasksFlow(): Flow<Map<Category, List<Task>>> {
        val tasksFlow = tasksDao.getTasksFlow().map { entities ->
            entities.map { it.toTask() }.sortedBy { it.index }
        }
        val categoriesFlow = categoryDao.getCategoriesFlow().map { entities ->
            entities.map { it.toCategory() }.sortedBy { it.index }
        }

        return tasksFlow.combine(categoriesFlow) { tasks, categories ->
            categories.associateWith { category ->
                tasks.filter { it.categoryId == category.id }
            }
        }
    }

    override suspend fun getTasks(): List<Task> {
       return tasksDao.getTasks().map { it.toTask() }
    }

    override suspend fun getCategories(): List<Category> {
        return categoryDao.getCategories().map { it.toCategory() }
    }

    override suspend fun upsertTask(task: Task) {
        tasksDao.upsertTask(task.toTaskEntity())
        // Schedule notifications for tasks with deadlines
        notificationScheduler.scheduleTaskDeadlineNotifications(task)
        // Update widget immediately
        widgetRepo.update()
    }

    override suspend fun deleteTask(task: Task) {
        tasksDao.deleteTask(task.toTaskEntity())
        // Cancel notifications for deleted task
        notificationScheduler.cancelTaskNotifications(task)
        // Update widget immediately
        widgetRepo.update()
    }

    override suspend fun deleteAllTasks() {
        tasksDao.deleteAllTasks()
        // Update widget immediately
        widgetRepo.update()
    }

    override suspend fun upsertCategory(category: Category) {
        categoryDao.upsertCategory(category.toCategoryEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category.toCategoryEntity())
    }

    override suspend fun deleteAllCategories() {
        categoryDao.deleteAllCategories()
    }
}