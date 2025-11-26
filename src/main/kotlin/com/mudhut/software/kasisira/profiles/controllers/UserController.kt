package com.mudhut.software.kasisira.profiles.controllers

import com.mudhut.software.kasisira.profiles.models.response.UserResponse
import com.mudhut.software.kasisira.profiles.services.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/users")
class UserController {

    @Autowired
    private lateinit var userService: UserService

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponse> {
        val user = userService.findById(id)
        return ResponseEntity.ok(user)
    }
}
