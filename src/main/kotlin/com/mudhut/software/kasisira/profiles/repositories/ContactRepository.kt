package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.Contact
import com.mudhut.software.kasisira.profiles.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ContactRepository : JpaRepository<Contact, Long> {

    fun findByUser(user: User): List<Contact>

    fun findByUserId(userId: Long): List<Contact>

    fun findByUserAndIsPrimary(user: User, isPrimary: Boolean): List<Contact>

    fun findByUserIdAndIsPrimaryTrue(userId: Long): List<Contact>

    fun findByPhoneNumber(phoneNumber: String): Contact?
}
