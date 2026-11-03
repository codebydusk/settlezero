package app.settlezero.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.settlezero.core.model.Expense
import app.settlezero.core.model.ExpenseSplitAllocation
import app.settlezero.core.model.SplitType

@Entity(
    tableName = "expense_splits",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expense_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("expense_id"),
        Index("member_id")
    ]
)
data class ExpenseSplitEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "expense_id")
    val expenseId: String,
    @ColumnInfo(name = "member_id")
    val memberId: String,
    @ColumnInfo(name = "allocated_amount_minor")
    val allocatedAmountMinor: Long
)

data class PopulatedExpense(
    @Embedded
    val expense: ExpenseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "expense_id"
    )
    val splits: List<ExpenseSplitEntity>
)

fun PopulatedExpense.asDomain(): Expense = Expense(
    id = expense.id,
    groupId = expense.groupId,
    description = expense.description,
    amountMinor = expense.amountMinor,
    payerMemberId = expense.payerMemberId,
    createdByMemberId = expense.createdByMemberId,
    expenseDate = expense.expenseDate,
    createdAt = expense.createdAt,
    note = expense.note,
    splitType = SplitType.valueOf(expense.splitType),
    isDeleted = expense.isDeleted,
    deletedAt = expense.deletedAt,
    deletedByMemberId = expense.deletedByMemberId,
    allocations = splits.map {
        ExpenseSplitAllocation(
            memberId = it.memberId,
            amountMinor = it.allocatedAmountMinor
        )
    }
)

