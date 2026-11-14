package org.shopflow.backend.database.repository

import org.bson.types.ObjectId
import org.shopflow.backend.database.model.User
import org.springframework.data.mongodb.repository.MongoRepository

interface UserRepository: MongoRepository<User, ObjectId> {
    fun findByEmail(email: String): User?
}