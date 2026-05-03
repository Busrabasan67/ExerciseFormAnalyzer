package com.example.exerciseformanalyzer.data.local.dao

// GroupDao — Sosyal grup ve üyelik sorguları
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.exerciseformanalyzer.data.local.entity.GroupEntity
import com.example.exerciseformanalyzer.data.local.entity.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    // --- GRUP CRUD ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity): Long

    // Kullanıcının dahil olduğu grupları izle
    // JOIN yerine basit subquery — üyelik tablosu ayrı, ama cache basit tutuldu
    @Query("SELECT * FROM groups WHERE firebaseDocId IN (SELECT groupFirebaseDocId FROM group_members WHERE userUid = :userUid)")
    fun observeGroupsForUser(userUid: String): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: Int): GroupEntity?

    @Query("SELECT * FROM groups WHERE isSynced = 0")
    suspend fun getUnsyncedGroups(): List<GroupEntity>

    @Query("UPDATE groups SET isSynced = 1, firebaseDocId = :docId WHERE id = :groupId")
    suspend fun markGroupAsSynced(groupId: Int, docId: String)

    @Query("SELECT * FROM groups WHERE firebaseDocId = :docId LIMIT 1")
    suspend fun getGroupByDocId(docId: String): GroupEntity?

    // --- ÜYELİK CRUD ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity): Long

    // Bir grubun tüm üyelerini izle — GroupDetailScreen için
    @Query("SELECT * FROM group_members WHERE groupFirebaseDocId = :groupDocId")
    fun observeMembersOfGroup(groupDocId: String): Flow<List<GroupMemberEntity>>

    // Kullanıcı gruptan ayrıldığında üyeliği sil
    @Query("DELETE FROM group_members WHERE groupFirebaseDocId = :groupDocId AND userUid = :userUid")
    suspend fun removeMember(groupDocId: String, userUid: String)

    @Query("SELECT * FROM group_members WHERE isSynced = 0")
    suspend fun getUnsyncedMembers(): List<GroupMemberEntity>

    @Query("UPDATE group_members SET isSynced = 1 WHERE id = :memberId")
    suspend fun markMemberAsSynced(memberId: Int)
}
