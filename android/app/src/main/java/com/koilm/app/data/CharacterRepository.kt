package com.koilm.app.data

import com.koilm.app.data.db.ChatDao
import com.koilm.app.data.db.CharacterDao
import com.koilm.app.data.db.CharacterEntity
import java.util.UUID

/**
 * ユーザーが作成する「相手(キャラクター)」の管理。
 * ユーザー自身がキャラ名・システムプロンプト(口調・性格・世界観)を自由に設定できる。
 */
class CharacterRepository(
    private val dao: CharacterDao,
    private val chatDao: ChatDao,
) {
    suspend fun listCharacters(): List<CharacterEntity> = dao.getAll()

    suspend fun getCharacter(id: String): CharacterEntity? = dao.getById(id)

    suspend fun createCharacter(name: String, systemPrompt: String): CharacterEntity {
        val character = CharacterEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            systemPrompt = systemPrompt,
        )
        dao.insert(character)
        return character
    }

    suspend fun updateCharacter(character: CharacterEntity) {
        dao.update(character)
    }

    suspend fun setNotificationsEnabled(character: CharacterEntity, enabled: Boolean) {
        dao.update(character.copy(notificationsEnabled = enabled))
    }

    /**
     * キャラクターとの関係を終える(「別れる」)。会話履歴・長期記憶サマリ・プロフィールメモリ・
     * 未消費の自発メッセージもすべて削除し、データを取り残さない。
     */
    suspend fun breakUpWith(character: CharacterEntity) {
        chatDao.deleteAllTurns(character.id)
        chatDao.deleteSummary(character.id)
        chatDao.deleteAllProfile(character.id)
        chatDao.deleteAllPendingProactiveMessages(character.id)
        dao.delete(character)
    }
}
