package org.every.nook.api.infrastructure.persistence.place

// Keep map, search and recent place access metadata aligned with active subscriptions.
internal const val PLACE_SHARE_TOKEN_SQL = """
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM user_saved_posts usp
                        INNER JOIN user_saved_post_places uspp ON uspp.user_saved_post_id = usp.id
                        WHERE usp.user_id = upb.user_id
                          AND usp.deleted_at IS NULL
                          AND uspp.place_id = p.id
                    ) THEN NULL
                    ELSE (
                        SELECT share_link.token_value
                        FROM shared_group_subscriptions subscription
                        INNER JOIN group_share_links share_link
                            ON share_link.id = subscription.share_link_id
                        INNER JOIN group_posts shared_group_post
                            ON shared_group_post.group_id = share_link.group_id
                        INNER JOIN user_groups shared_group ON shared_group.id = shared_group_post.group_id
                        INNER JOIN user_saved_posts shared_saved_post
                            ON shared_saved_post.id = shared_group_post.user_saved_post_id
                        INNER JOIN user_saved_post_places shared_saved_post_place
                            ON shared_saved_post_place.user_saved_post_id = shared_saved_post.id
                        WHERE subscription.member_id = upb.user_id
                          AND shared_saved_post_place.place_id = p.id
                          AND share_link.revoked_at IS NULL
                          AND (share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6))
                          AND shared_group_post.deleted_at IS NULL
                          AND shared_group.deleted_at IS NULL
                          AND shared_saved_post.deleted_at IS NULL
"""

internal const val PLACE_SHARE_TOKEN_END_SQL = """
                        ORDER BY subscription.created_at DESC, subscription.id DESC
                        LIMIT 1
                    )
                END
"""

internal const val FILTERED_PLACE_SHARE_TOKEN_SQL = """
    $PLACE_SHARE_TOKEN_SQL
    AND (:filterByGroups = false OR shared_group.id IN (:groupIds))
    $PLACE_SHARE_TOKEN_END_SQL
"""
