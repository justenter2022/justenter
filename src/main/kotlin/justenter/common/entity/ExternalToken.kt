package justenter.common.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "external_token")
class ExternalToken(
    @Id
    @Column(name = "company", length = 100)
    val company: String = "CJ",  // PK로 사용 (고정값: CJ)

    @Column(name = "token", nullable = false, unique = true, length = 40)
    var token: String,

    @Column(name = "token_expire_dtm", nullable = false, length = 14)
    var tokenExpireDtm: String,

    @Column(name = "reg_id", nullable = false, length = 20)
    val regId: String = "SYSTEM",

    @Column(name = "reg_time", nullable = false, updatable = false)
    val regTime: LocalDateTime = LocalDateTime.now(),

    @Column(name = "upd_time", nullable = false)
    var updTime: LocalDateTime = LocalDateTime.now()
)