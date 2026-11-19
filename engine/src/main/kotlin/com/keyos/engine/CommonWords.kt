package com.keyos.engine

/**
 * Small built-in English starter list (original, roughly by frequency). It only seeds suggestions;
 * the keyboard learns the user's own words locally. A full licensed dictionary comes in Phase 4.
 */
object CommonWords {
    val list: List<String> = (
        "the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she " +
            "or an will my one all would there their what so up out if about who get which go me when make can like time " +
            "no just him know take people into year your good some could them see other than then now look only come its " +
            "over think also back after use two how our work first well way even new want because any these give day most " +
            "us is are was were been has had did does am hello hi hey thanks thank please sorry yes okay ok today tomorrow " +
            "tonight morning night evening home where why here going coming need love really very much more still always " +
            "never again already something nothing everything anything someone everyone little great right call message " +
            "phone friend friends family mother father brother sister school office meeting party birthday happy welcome " +
            "tell ask help feel try leave put mean keep let begin seem talk turn start show hear play run move live believe " +
            "bring happen write provide sit stand lose pay meet include continue set learn change lead understand watch " +
            "follow stop create speak read allow add spend grow open walk win offer remember consider appear buy wait serve " +
            "send expect build stay fall cut reach remain many those before between through while should being long own " +
            "same another both each few such down off last next must might every find got going gonna wanna food water " +
            "money job city country world life man woman child children place thing things week month year years hour " +
            "minute second number name problem question answer idea story point fact part group company system program " +
            "government important different small large big high old young early late sure able ready free full best better " +
            "bad true real hard easy hope wish maybe perhaps soon later again together without within during since until " +
            "though although however whether where there everything beautiful amazing awesome wonderful congratulations " +
            "tonight lunch dinner breakfast pictures photo video music movie game book car bus train flight ticket " +
            "address email number account password internet online website app update download"
        ).split(' ').filter { it.isNotBlank() }.distinct()
}
