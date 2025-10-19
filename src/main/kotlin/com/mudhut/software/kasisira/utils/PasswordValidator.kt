package com.mudhut.software.kasisira.utils

import org.springframework.stereotype.Component

@Component
class PasswordValidator {
    fun validatePassword(password: String) {
        require(password.length >= 8) { "Password must be at least 8 characters long" }

        var hasLetter = false
        var hasDigit = false
        var hasSpecial = false

        for (c in password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true
            } else if (Character.isDigit(c)) {
                hasDigit = true
            } else if (!Character.isWhitespace(c)) {
                hasSpecial = true
            }
        }

        require(!(!hasLetter || !hasDigit || !hasSpecial)) { "Password must contain at least one letter, one number, and one special character" }
    }
}
