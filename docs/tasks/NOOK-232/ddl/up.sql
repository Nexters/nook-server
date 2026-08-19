UPDATE user_saved_post_places saved_place
INNER JOIN user_saved_posts saved_post
    ON saved_post.id = saved_place.user_saved_post_id
INNER JOIN post_places post_place
    ON post_place.post_id = saved_post.post_id
    AND post_place.place_id = saved_place.place_id
INNER JOIN post_media source_media
    ON source_media.post_id = post_place.post_id
    AND source_media.media_type = 'IMAGE'
    AND source_media.display_order = post_place.source_media_sequence
SET saved_place.thumbnail_url = NULL
WHERE saved_place.thumbnail_url = source_media.media_url;
