package ru.netology.nmedia.service

import jakarta.transaction.Transactional
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import ru.netology.nmedia.dto.PushToken
import ru.netology.nmedia.dto.Token
import ru.netology.nmedia.dto.User
import ru.netology.nmedia.entity.PushTokenEntity
import ru.netology.nmedia.entity.TokenEntity
import ru.netology.nmedia.entity.UserEntity
import ru.netology.nmedia.exception.NotFoundException
import ru.netology.nmedia.exception.PasswordNotMatchException
import ru.netology.nmedia.exception.UserRegisteredException
import ru.netology.nmedia.extensions.principalOrNull
import ru.netology.nmedia.repository.PushTokenRepository
import ru.netology.nmedia.repository.TokenRepository
import ru.netology.nmedia.repository.UserRepository
import java.security.SecureRandom
import java.util.*

@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val tokenRepository: TokenRepository,
    private val pushTokenRepository: PushTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mediaService: MediaService,
) : UserDetailsService {
    fun create(login: String, pass: String, name: String, avatar: String): User = userRepository.save(
        UserEntity(
            0L,
            login,
            passwordEncoder.encode(pass),
            name,
            avatar,
        )
    ).toDto()

    fun register(login: String, pass: String, name: String, file: MultipartFile?): Token {
        if (userRepository.findByLogin(login) != null) {
            throw UserRegisteredException()
        }

        val avatar = file?.let {
            mediaService.saveAvatar(it)
        }

        return userRepository.save(
            UserEntity(
                0L,
                login,
                passwordEncoder.encode(pass),
                name,
                avatar?.id ?: "", // TODO:
            )
        ).let { user ->
            val token = Token(user.id, generateToken())
            tokenRepository.save(TokenEntity(token.token, user))
            token
        }
    }

    fun login(login: String, pass: String): Token = userRepository
        .findByLogin(login)
        ?.let { user ->
            if (!passwordEncoder.matches(pass, user.password)) {
                throw PasswordNotMatchException()
            }
            val token = Token(user.id, generateToken())
            tokenRepository.save(TokenEntity(token.token, user))
            token
        } ?: throw NotFoundException()

    fun getByLogin(login: String): User? = userRepository
        .findByLogin(login)
        ?.toDto()

    fun getByToken(token: String): User? = tokenRepository
        .findByIdOrNull(token)
        ?.user
        ?.toDto()

    override fun loadUserByUsername(username: String?): UserDetails =
        userRepository.findByLogin(username) ?: throw UsernameNotFoundException(username)

    fun saveInitialToken(userId: Long, value: String): Token =
        userRepository.findByIdOrNull(userId)
            ?.let { user ->
                val token = Token(userId, value)
                tokenRepository.save(TokenEntity(token.token, user))
                token
            } ?: throw NotFoundException()

    private fun generateToken(): String = ByteArray(128).apply {
        SecureRandom().nextBytes(this)
    }.let {
        Base64.getEncoder().withoutPadding().encodeToString(it)
    }

    fun saveToken(pushToken: PushToken) {
        val userId = principalOrNull()?.id ?: 0
        pushTokenRepository.findByToken(pushToken.token)
            .orElse(PushTokenEntity(0, pushToken.token, userId))
            .let {
                if (it.id == 0L) pushTokenRepository.save(it) else it.userId = userId
            }
    }
}