ALTER TABLE user_place_bookmarks
    ADD KEY idx_user_id_created_at_id (user_id, created_at DESC, id DESC);
