package com.mudhut.software.kasisira.properties.services

import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.properties.mappers.PropertyMediaMapper
import com.mudhut.software.kasisira.properties.models.request.AddMediaRequest
import com.mudhut.software.kasisira.properties.models.request.UpdateMediaRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyMediaResponse
import com.mudhut.software.kasisira.properties.repositories.PropertyMediaRepository
import com.mudhut.software.kasisira.properties.repositories.PropertyRepository
import com.mudhut.software.kasisira.utils.exceptions.MediaNotFoundException
import com.mudhut.software.kasisira.utils.exceptions.PropertyNotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PropertyMediaServiceImpl : PropertyMediaService {

    @Autowired
    private lateinit var propertyMediaRepository: PropertyMediaRepository

    @Autowired
    private lateinit var propertyRepository: PropertyRepository

    @Autowired
    private lateinit var propertyMediaMapper: PropertyMediaMapper

    @Autowired
    private lateinit var membershipService: MembershipService

    override fun addMedia(propertyId: Long, callerUserId: Long, request: AddMediaRequest): PropertyMediaResponse {
        val property = propertyRepository.findById(propertyId)
            .orElseThrow { PropertyNotFoundException("Property with id $propertyId not found") }

        membershipService.requirePermission(callerUserId, property.ownerOrg.id, Permission.MANAGE_LISTINGS)

        val media = propertyMediaMapper.fromAddRequest(request, property)

        if (request.isPrimary) {
            propertyMediaRepository.clearPrimaryFlagExcept(propertyId, 0)
        }

        val savedMedia = propertyMediaRepository.save(media)
        return propertyMediaMapper.toResponse(savedMedia)
    }

    override fun getMediaByPropertyId(propertyId: Long): List<PropertyMediaResponse> {
        return propertyMediaRepository.findByPropertyIdOrderByDisplayOrder(propertyId)
            .map { propertyMediaMapper.toResponse(it) }
    }

    override fun updateMedia(mediaId: Long, callerUserId: Long, request: UpdateMediaRequest): PropertyMediaResponse {
        val media = propertyMediaRepository.findById(mediaId)
            .orElseThrow { MediaNotFoundException("Media with id $mediaId not found") }

        val property = media.property
            ?: throw PropertyNotFoundException("Property not found for media $mediaId")
        membershipService.requirePermission(callerUserId, property.ownerOrg.id, Permission.MANAGE_LISTINGS)

        val updatedMedia = media.copy(
            description = request.description ?: media.description,
            displayOrder = request.displayOrder ?: media.displayOrder
        )

        val savedMedia = propertyMediaRepository.save(updatedMedia)
        return propertyMediaMapper.toResponse(savedMedia)
    }

    override fun deleteMedia(mediaId: Long, callerUserId: Long) {
        val media = propertyMediaRepository.findById(mediaId)
            .orElseThrow { MediaNotFoundException("Media with id $mediaId not found") }

        val property = media.property
            ?: throw PropertyNotFoundException("Property not found for media $mediaId")
        membershipService.requirePermission(callerUserId, property.ownerOrg.id, Permission.MANAGE_LISTINGS)

        propertyMediaRepository.delete(media)
    }

    override fun setAsPrimary(mediaId: Long, callerUserId: Long): PropertyMediaResponse {
        val media = propertyMediaRepository.findById(mediaId)
            .orElseThrow { MediaNotFoundException("Media with id $mediaId not found") }

        val property = media.property
            ?: throw PropertyNotFoundException("Property not found for media $mediaId")
        membershipService.requirePermission(callerUserId, property.ownerOrg.id, Permission.MANAGE_LISTINGS)

        val propertyId = property.id

        propertyMediaRepository.clearPrimaryFlagExcept(propertyId, mediaId)

        val updatedMedia = media.copy(isPrimary = true)
        val savedMedia = propertyMediaRepository.save(updatedMedia)

        return propertyMediaMapper.toResponse(savedMedia)
    }

    override fun reorderMedia(propertyId: Long, callerUserId: Long, mediaIds: List<Long>) {
        val property = propertyRepository.findById(propertyId)
            .orElseThrow { PropertyNotFoundException("Property with id $propertyId not found") }

        membershipService.requirePermission(callerUserId, property.ownerOrg.id, Permission.MANAGE_LISTINGS)

        mediaIds.forEachIndexed { index, mediaId ->
            val media = propertyMediaRepository.findById(mediaId)
                .orElseThrow { MediaNotFoundException("Media with id $mediaId not found") }

            val updatedMedia = media.copy(displayOrder = index)
            propertyMediaRepository.save(updatedMedia)
        }
    }
}
