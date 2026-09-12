import time

def add_activity(con, user_id, username, action_type, action_text, link, emoji):
    try:
        con.execute(
            '''
            INSERT INTO community_activities
            (user_id, username, action_type, action_text, link, emoji, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ''',
            (
                user_id,
                username,
                action_type,
                action_text,
                link,
                emoji,
                int(time.time())
            )
        )
        return True
    except Exception as e:
        print("Activity error:", e)
        return False
