UNWIND range(1, 30) AS i
CREATE (:User {name: 'User_' + i});
UNWIND range(1, 20) AS i
CREATE (:Stream {
    id: 'stream' + i,
    name: 'Stream_' + i,
    description: 'Description for stream ' + i,
    followers: i * 100,
    createdAt: datetime(),
    total_view_count: toInteger(rand() * 100000),
    url: 'https://stream_' + i + '.example.com'
});
UNWIND ['Fortnite', 'Serious Sam', 'Minecraft', 'League of Legends', 'Valorant'] AS gameName
CREATE (:Game {name: gameName});
UNWIND ['German', 'Japanese', 'Italian'] AS languageName
CREATE (:Language {name: languageName});
MATCH (s:Stream), (g:Game)
WITH s, g
WHERE rand() < 0.4
MERGE (s)-[:PLAYS]->(g);
MATCH (s:Stream)
WITH s
MATCH (l:Language)
WITH s, l
ORDER BY rand()
WITH s, head(collect(l)) AS selectedLanguage
MERGE (s)-[:HAS_LANGUAGE]->(selectedLanguage);

UNWIND range(1, 10) AS i
CREATE (:Team {
    id: 'team' + i,
    name: 'Team_' + i,
    createdAt: datetime()
});
MATCH (u:User), (s:Stream)
WITH u, s
WHERE rand() < 0.3
MERGE (u)-[:VIP]->(s)
WITH u, s
WHERE rand() < 0.5
MERGE (u)-[:MODERATOR]->(s)
WITH u, s
WHERE rand() < 0.8
MERGE (u)-[:CHATTER]->(s);
MATCH (s:Stream), (t:Team)
WITH s, t
WHERE rand() < 0.4
MERGE (s)-[:HAS_TEAM]->(t);
MATCH (u:User {name: 'User_1'}), (s:Stream {name: 'Stream_1'})
MERGE (u)-[:VIP]->(s);
MATCH (u:User {name: 'User_2'}), (s:Stream {name: 'Stream_2'})
MERGE (u)-[:MODERATOR]->(s);
MATCH (s:Stream {name: 'Stream_1'}), (g:Game {name: 'Fortnite'})
MERGE (s)-[:PLAYS]->(g);
MATCH (s:Stream {name: 'Stream_2'}), (g:Game {name: 'Serious Sam'})
MERGE (s)-[:PLAYS]->(g);
MATCH (u:User {name: 'User_1'}), (g:Game {name: 'Fortnite'})
WITH u, g
MATCH (s:Stream)-[:PLAYS]->(g)
MERGE (u)-[:CHATTER]->(s);
MATCH (u:User {name: 'User_2'}), (g:Game {name: 'Serious Sam'})
WITH u, g
MATCH (s:Stream)-[:PLAYS]->(g)
MERGE (u)-[:CHATTER]->(s)
