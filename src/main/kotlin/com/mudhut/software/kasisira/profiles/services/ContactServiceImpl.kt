package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.mappers.ContactMapper
import com.mudhut.software.kasisira.profiles.models.request.CreateContactRequest
import com.mudhut.software.kasisira.profiles.models.request.UpdateContactRequest
import com.mudhut.software.kasisira.profiles.models.response.ContactResponse
import com.mudhut.software.kasisira.profiles.repositories.ContactRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.utils.exceptions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.regex.Pattern

@Service
@Transactional
class ContactServiceImpl : ContactService {

    @Autowired
    private lateinit var contactRepository: ContactRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var contactMapper: ContactMapper

    companion object {
        private const val PHONE_PATTERN = "^\\+[1-9]\\d{1,14}$"
    }

    override fun findById(id: Long): ContactResponse {
        val contact = contactRepository.findById(id)
            .orElseThrow { ContactNotFoundException("Contact with id $id not found") }
        return contactMapper.toResponse(contact)
    }

    override fun findByUserId(userId: Long): List<ContactResponse> {
        val contacts = contactRepository.findByUserId(userId)
        return contactMapper.toResponseList(contacts)
    }

    override fun findPrimaryContactsByUserId(userId: Long): List<ContactResponse> {
        val contacts = contactRepository.findByUserIdAndIsPrimaryTrue(userId)
        return contactMapper.toResponseList(contacts)
    }

    override fun createContact(userId: Long, request: CreateContactRequest): ContactResponse {
        // Validate phone number format
        if (!Pattern.compile(PHONE_PATTERN).matcher(request.phoneNumber).matches()) {
            throw InvalidPhoneNumberException("Invalid phone number format")
        }

        // Verify user exists
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User with id $userId not found") }

        // Map request to entity
        val contact = contactMapper.fromCreateRequest(request)
        contact.user = user

        // If this is set as primary, unset all other primary contacts for this user
        if (request.isPrimary) {
            val userContacts = contactRepository.findByUserId(userId)
            userContacts.forEach { userContact ->
                if (userContact.isPrimary) {
                    val updatedContact = userContact.copy(isPrimary = false)
                    contactRepository.save(updatedContact)
                }
            }
        }

        // Save contact
        val savedContact = contactRepository.save(contact)
        return contactMapper.toResponse(savedContact)
    }

    override fun updateContact(id: Long, request: UpdateContactRequest): ContactResponse {
        val existingContact = contactRepository.findById(id)
            .orElseThrow { ContactNotFoundException("Contact with id $id not found") }

        // Validate phone number format if provided
        if (request.phoneNumber != null && !Pattern.compile(PHONE_PATTERN).matcher(request.phoneNumber).matches()) {
            throw InvalidPhoneNumberException("Invalid phone number format")
        }

        // If setting as primary, unset all other primary contacts for this user
        if (request.isPrimary == true && !existingContact.isPrimary) {
            val userId = existingContact.user?.id
                ?: throw IllegalStateException("Contact does not have an associated user")

            val userContacts = contactRepository.findByUserId(userId)
            userContacts.forEach { userContact ->
                if (userContact.isPrimary && userContact.id != id) {
                    val updatedContact = userContact.copy(isPrimary = false)
                    contactRepository.save(updatedContact)
                }
            }
        }

        // Update only provided fields
        val updatedContact = existingContact.copy(
            phoneNumber = request.phoneNumber ?: existingContact.phoneNumber,
            isPrimary = request.isPrimary ?: existingContact.isPrimary,
            label = request.label ?: existingContact.label
        )

        val savedContact = contactRepository.save(updatedContact)
        return contactMapper.toResponse(savedContact)
    }

    override fun deleteContact(id: Long) {
        if (!contactRepository.existsById(id)) {
            throw ContactNotFoundException("Contact with id $id not found")
        }
        contactRepository.deleteById(id)
    }

    override fun findAllContacts(): List<ContactResponse> {
        val contacts = contactRepository.findAll()
        return contactMapper.toResponseList(contacts)
    }

    override fun setPrimaryContact(contactId: Long, userId: Long): ContactResponse {
        val contact = contactRepository.findById(contactId)
            .orElseThrow { ContactNotFoundException("Contact with id $contactId not found") }

        // Verify that the contact belongs to the user
        if (contact.user?.id != userId) {
            throw IllegalArgumentException("Contact does not belong to user with id $userId")
        }

        // Unset all other primary contacts for this user
        val userContacts = contactRepository.findByUserId(userId)
        userContacts.forEach { userContact ->
            if (userContact.isPrimary && userContact.id != contactId) {
                val updatedContact = userContact.copy(isPrimary = false)
                contactRepository.save(updatedContact)
            }
        }

        // Set this contact as primary
        val updatedContact = contact.copy(isPrimary = true)
        val savedContact = contactRepository.save(updatedContact)
        return contactMapper.toResponse(savedContact)
    }

    override fun verifyContact(contactId: Long): ContactResponse {
        val contact = contactRepository.findById(contactId)
            .orElseThrow { ContactNotFoundException("Contact with id $contactId not found") }

        val updatedContact = contact.copy(isVerified = true)
        val savedContact = contactRepository.save(updatedContact)
        return contactMapper.toResponse(savedContact)
    }
}
