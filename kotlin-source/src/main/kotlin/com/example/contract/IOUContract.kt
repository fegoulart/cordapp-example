package com.example.contract

import com.example.state.IOUState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOU].
 *
 * For a new [IOU] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOU].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
open class IOUContract : Contract {
    companion object {
        @JvmStatic
        val IOU_CONTRACT_ID = "com.example.contract.IOUContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        //val command = tx.commands.requireSingleCommand<Commands.Create>()
        val command = tx.commands.single()

        when (command.value) {
            is Commands.Create -> verifyCreate(tx, command)
            is Commands.Pay -> verifyPay(tx, command)
            else -> throw IllegalAccessException("Command not recognized.")
        }
    }

    private fun verifyPay(tx: LedgerTransaction, command: CommandWithParties<CommandData>) {
        requireThat {
            val output = tx.outputsOfType<IOUState>().single() //aqui significa que eu quero ou assinar ou pagar uma transacao
            val input = tx.inputsOfType<IOUState>().single()

            //tem 1 input
            "Only one input allowed." using (tx.inputs.size == 1)

            //tem 1 output
            "Only one output state should be created." using (tx.outputs.size == 1)

            //os dois assinaram


            "Everyone should sign the transaction." using (
                    command.signers.containsAll(
                            tx.outputs
                                    .flatMap { it.data.participants }
                                    .map { it.owningKey })
                    )

            //valor pago > zero
            "Payment ammount must be > 0." using (output.paymentValue > 0)

            //valor pago >= valor emprestado
            "Payment >= value." using (output.paymentValue > output.value)


            //status == Criado
            "Input status should be 'Criado'." using (input.status == "Criado")

            //status saida == Pago
            "Output status should be 'Pago'." using (output.status == "Pago")

            //tem que pagar com juros
            "Interested rate should be payed in full" using (interestedPayed(output))

            /*"The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
            "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

            // IOU-specific constraints.
            "The IOU's value must be non-negative." using (out.value > 0)

            //novas regras de negocio
            //status criado

            // juros >= 0
            "The interest value should be positive." using (out.interest >= 0)
            // date > ontem
            "The due date shouldn´t be in the past." using (Instant.now().minus(1, ChronoUnit.DAYS).isBefore(out.dueDate))
            // payment value = 0
            "shouldn´t be initialized." using (out.paymentValue == 0)

*/

        }
    }


    private fun interestedPayed(state: IOUState): Boolean {
        val monthsLate = (Instant.now().epochSecond - state.dueDate.epochSecond) / 60 / 60 / 24 / 30


        if (monthsLate > 0) {
            //returns
            val valueToPay = Math.floor(
                    (1 + Math.pow(
                            state.interest.toDouble() / 100,
                            monthsLate.toDouble()
                                    .times(state.value))
                            )).toInt()
            return valueToPay == state.paymentValue


        } else if (monthsLate <= 0) {
            return state.paymentValue == state.value
        }
        return false
    }

    private fun verifyCreate(tx: LedgerTransaction, command: CommandWithParties<CommandData>) {
        requireThat {
            // Generic constraints around the IOU transaction.
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            "Only one output state should be created." using (tx.outputs.size == 1)
            val out = tx.outputsOfType<IOUState>().single() //aqui significa que eu quero ou assinar ou pagar uma transacao
            "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)
            "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

            // IOU-specific constraints.
            "The IOU's value must be non-negative." using (out.value > 0)

            //novas regras de negocio
            //status criado
            "Status should be 'Criado'." using (out.status == "Criado")

            // juros >= 0
            "The interest value should be positive." using (out.interest >= 0)
            // date > ontem
            "The due date shouldn´t be in the past." using (Instant.now().minus(1, ChronoUnit.DAYS).isBefore(out.dueDate))
            // payment value = 0
            "shouldn´t be initialized." using (out.paymentValue == 0)

        }
    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
        class Pay : Commands
    }
}
