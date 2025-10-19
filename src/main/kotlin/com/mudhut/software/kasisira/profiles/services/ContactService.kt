package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.models.request.CreateContactRequest
import com.mudhut.software.kasisira.profiles.models.request.UpdateContactRequest
import com.mudhut.software.kasisira.profiles.models.response.ContactResponse

interface ContactService {

    fun findById(id: Long): ContactResponse

    fun findByUserId(userId: Long): List<ContactResponse>

    fun findPrimaryContactsByUserId(userId: Long): List<ContactResponse>

    fun createContact(userId: Long, request: CreateContactRequest): ContactResponse

    fun updateContact(id: Long, request: UpdateContactRequest): ContactResponse

    fun deleteContact(id: Long)

    fun findAllContacts(): List<ContactResponse>

    fun setPrimaryContact(contactId: Long, userId: Long): ContactResponse

    fun verifyContact(contactId: Long): ContactResponse
}
