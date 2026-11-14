package org.shopflow.backend.database.repository

import org.bson.types.ObjectId
import org.shopflow.backend.database.model.RefreshToken
import org.springframework.data.mongodb.repository.MongoRepository

interface RefreshTokenRepository: MongoRepository<RefreshToken, ObjectId> {
    fun findByUserIdAndHashedToken(userId: ObjectId, hashedToken: String): RefreshToken?
    fun deleteByUserIdAndHashedToken(userId: ObjectId, hashedToken: String)
}