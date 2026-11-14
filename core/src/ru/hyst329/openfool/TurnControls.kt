package ru.hyst329.openfool

/** One policy for both button visibility and callbacks, independent of rendering. */
internal object TurnControls {
    data class Actions(val take: Boolean = false, val done: Boolean = false, val give: Boolean = false)
    fun actions(settled: Boolean, dragging: Boolean, attacks: Int, defenses: Int,
                humanDefender: Boolean, humanThrower: Boolean, taking: Boolean,
                alreadyDone: Boolean): Actions {
        if (!settled || dragging || attacks == 0) return Actions()
        val throwing = humanThrower && !humanDefender && !alreadyDone
        return Actions(
            take = humanDefender && !taking && defenses < attacks,
            done = throwing && !taking && defenses == attacks,
            give = throwing && taking
        )
    }
    fun showYourTurn(actions: Actions,humanOwner: Boolean,interactive: Boolean,taking: Boolean,alreadyDone: Boolean): Boolean =
        interactive && humanOwner && !taking && !alreadyDone && !actions.take && !actions.done && !actions.give

    fun botFinishesDefendedHumanTurn(humanDefender: Boolean, attacks: Int, defenses: Int): Boolean =
        humanDefender && attacks > 0 && attacks == defenses
}
