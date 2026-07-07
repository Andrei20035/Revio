package com.revio.app.di

import com.revio.app.data.repository.ActivityRepository
import com.revio.app.data.repository.ActivityRepositoryImpl
import com.revio.app.data.repository.AuthRepository
import com.revio.app.data.repository.AuthRepositoryImpl
import com.revio.app.data.repository.LeaderboardRepository
import com.revio.app.data.repository.LeaderboardRepositoryImpl
import com.revio.app.data.repository.CarModelRepository
import com.revio.app.data.repository.CarModelRepositoryImpl
import com.revio.app.data.repository.CommentRepository
import com.revio.app.data.repository.CommentRepositoryImpl
import com.revio.app.data.repository.FriendRepositoryImpl
import com.revio.app.data.repository.FriendRepository
import com.revio.app.data.repository.FriendRequestRepositoryImpl
import com.revio.app.data.repository.FriendRequestRepository
import com.revio.app.data.repository.ImageRepository
import com.revio.app.data.repository.ImageRepositoryImpl
import com.revio.app.data.repository.LikeRepository
import com.revio.app.data.repository.LikeRepositoryImpl
import com.revio.app.data.repository.LocationRepository
import com.revio.app.data.repository.LocationRepositoryImpl
import com.revio.app.data.repository.PostRepository
import com.revio.app.data.repository.PostRepositoryImpl
import com.revio.app.data.repository.ReportRepository
import com.revio.app.data.repository.ReportRepositoryImpl
import com.revio.app.data.repository.UserCarRepository
import com.revio.app.data.repository.UserCarRepositoryImpl
import com.revio.app.data.repository.UserRepository
import com.revio.app.data.repository.UserRepositoryImpl
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
    abstract fun bindImageRepository(
        impl: ImageRepositoryImpl
    ): ImageRepository

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
}
