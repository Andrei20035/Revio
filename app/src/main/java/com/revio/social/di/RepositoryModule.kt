package com.revio.social.di

import com.revio.social.data.image.CoilFeedImagePrefetcher
import com.revio.social.data.image.FeedImagePrefetcher
import com.revio.social.data.local.cache.FeedCache
import com.revio.social.data.local.cache.RoomFeedCache
import com.revio.social.data.repository.ActivityRepository
import com.revio.social.data.repository.ActivityRepositoryImpl
import com.revio.social.data.repository.AuthRepository
import com.revio.social.data.repository.AuthRepositoryImpl
import com.revio.social.data.repository.LeaderboardRepository
import com.revio.social.data.repository.LeaderboardRepositoryImpl
import com.revio.social.data.repository.CarModelRepository
import com.revio.social.data.repository.CarModelRepositoryImpl
import com.revio.social.data.repository.CommentRepository
import com.revio.social.data.repository.CommentRepositoryImpl
import com.revio.social.data.repository.FriendRepositoryImpl
import com.revio.social.data.repository.FriendRepository
import com.revio.social.data.repository.FriendRequestRepositoryImpl
import com.revio.social.data.repository.FriendRequestRepository
import com.revio.social.data.repository.LikeRepository
import com.revio.social.data.repository.LikeRepositoryImpl
import com.revio.social.data.repository.LocationRepository
import com.revio.social.data.repository.LocationRepositoryImpl
import com.revio.social.data.repository.PostRepository
import com.revio.social.data.repository.PostRepositoryImpl
import com.revio.social.data.repository.ReportRepository
import com.revio.social.data.repository.ReportRepositoryImpl
import com.revio.social.data.repository.UserCarRepository
import com.revio.social.data.repository.UserCarRepositoryImpl
import com.revio.social.data.repository.UserRepository
import com.revio.social.data.repository.UserRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindLeaderboardRepository(
        impl: LeaderboardRepositoryImpl
    ): LeaderboardRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCarModelRepository(
        impl: CarModelRepositoryImpl
    ): CarModelRepository

    @Binds
    @Singleton
    abstract fun bindCommentRepository(
        impl: CommentRepositoryImpl
    ): CommentRepository

    @Binds
    @Singleton
    abstract fun bindFriendRepository(
        impl: FriendRepositoryImpl
    ): FriendRepository

    @Binds
    @Singleton
    abstract fun bindFriendRequestRepository(
        impl: FriendRequestRepositoryImpl
    ): FriendRequestRepository

    @Binds
    @Singleton
    abstract fun bindLikeRepository(
        impl: LikeRepositoryImpl
    ): LikeRepository

    @Binds
    @Singleton
    abstract fun bindPostRepository(
        impl: PostRepositoryImpl
    ): PostRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(
        impl: ReportRepositoryImpl
    ): ReportRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindUserCarRepository(
        impl: UserCarRepositoryImpl
    ): UserCarRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        impl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindActivityRepository(
        impl: ActivityRepositoryImpl
    ): ActivityRepository

    @Binds
    @Singleton
    abstract fun bindFeedCache(
        impl: RoomFeedCache
    ): FeedCache

    @Binds
    @Singleton
    abstract fun bindFeedImagePrefetcher(
        impl: CoilFeedImagePrefetcher
    ): FeedImagePrefetcher
}
