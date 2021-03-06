package com.yh20studio.springbootwebservice.service;

import com.yh20studio.springbootwebservice.component.JwtUtil;
import com.yh20studio.springbootwebservice.domain.accessTokenBlackList.AccessTokenBlackList;
import com.yh20studio.springbootwebservice.domain.accessTokenBlackList.AccessTokenBlackListRepository;
import com.yh20studio.springbootwebservice.domain.exception.RestException;
import com.yh20studio.springbootwebservice.domain.member.Member;
import com.yh20studio.springbootwebservice.domain.member.MemberRepository;
import com.yh20studio.springbootwebservice.domain.refreshToken.RefreshToken;
import com.yh20studio.springbootwebservice.domain.refreshToken.RefreshTokenRepository;
import com.yh20studio.springbootwebservice.dto.httpResponse.MessageResponse;
import com.yh20studio.springbootwebservice.dto.member.MemberSaveRequestDto;
import com.yh20studio.springbootwebservice.dto.token.AccessTokenRequestDto;
import com.yh20studio.springbootwebservice.dto.token.TokenRequestDto;
import com.yh20studio.springbootwebservice.dto.token.TokenResponseDto;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@AllArgsConstructor
public class AuthService {
    private AuthenticationManagerBuilder authenticationManagerBuilder;
    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private RefreshTokenRepository refreshTokenRepository;
    private AccessTokenBlackListRepository accessTokenBlackListRepository;

    @Transactional
    public Long signup(MemberSaveRequestDto memberSaveRequestDto) {
        if(memberRepository.existsByEmail(memberSaveRequestDto.getEmail())){
            //409 Error
            throw new RestException(HttpStatus.CONFLICT, "?????? ???????????? ?????? ???????????????.");
        }

        Member member = memberSaveRequestDto.toMember(passwordEncoder);

        return memberRepository.save(member).getId();
    }

    @Transactional
    public TokenResponseDto login(MemberSaveRequestDto memberSaveRequestDto) {
        UsernamePasswordAuthenticationToken authenticationToken = memberSaveRequestDto.toAuthentication();

        // 2. ????????? ?????? (????????? ???????????? ??????) ??? ??????????????? ??????
        //    authenticate ???????????? ????????? ??? ??? CustomUserDetailsService ?????? ???????????? loadUserByUsername ???????????? ?????????
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        // 3. ?????? ????????? ???????????? JWT ?????? ??????
        TokenResponseDto tokenResponseDto = jwtUtil.generateTokenDto(authentication);

        // 4. RefreshToken ??????
        RefreshToken refreshToken = RefreshToken.builder()
                .key(authentication.getName())
                .value(tokenResponseDto.getRefreshToken())
                .expires(tokenResponseDto.getRefreshTokenExpiresIn())
                .build();

        refreshTokenRepository.save(refreshToken);

        // 5. ?????? ??????
        return tokenResponseDto;
    }

    @Transactional
    public MessageResponse logout(TokenRequestDto tokenRequestDto) {

        if (!jwtUtil.validateToken(tokenRequestDto.getRefreshToken())) {
            //401 Error
            throw new RestException(HttpStatus.UNAUTHORIZED, "Refresh Token ??? ???????????? ????????????.");
        }

        Authentication authentication = jwtUtil.getAuthentication(tokenRequestDto.getAccessToken());

        RefreshToken refreshToken = refreshTokenRepository.findByKey(authentication.getName())
                //401 Error
                .orElseThrow(() -> new RestException(HttpStatus.UNAUTHORIZED, "???????????? ??? ??????????????????."));

        if (!refreshToken.getValue().equals(tokenRequestDto.getRefreshToken())) {
            //401 Error
            throw new RestException(HttpStatus.UNAUTHORIZED, "????????? ?????? ????????? ???????????? ????????????.");
        }

        // AccessToken ??????????????? ??????
        AccessTokenRequestDto accessTokenRequestDto = AccessTokenRequestDto.builder()
                .key(refreshToken.getKey())
                .value(tokenRequestDto.getAccessToken())
                .expires(tokenRequestDto.getAccessTokenExpiresIn())
                .build();

        accessTokenBlackListRepository.save(accessTokenRequestDto.toEntity());

        // RefreshToken ??????
        refreshTokenRepository.deleteByKey((refreshToken.getKey()));

        return new MessageResponse("Logout");
    }

    @Transactional
    public TokenResponseDto reissue(TokenRequestDto tokenRequestDto) {
        // 1. Refresh Token ??????
        if (!jwtUtil.validateToken(tokenRequestDto.getRefreshToken())) {
            //401 Error
            throw new RestException(HttpStatus.UNAUTHORIZED, "Refresh Token ??? ???????????? ????????????");
        }

        // 2. Access Token ?????? Member ID ????????????
        Authentication authentication = jwtUtil.getAuthentication(tokenRequestDto.getAccessToken());

        // 3. ??????????????? Member ID ??? ???????????? Refresh Token ??? ?????????
        RefreshToken refreshToken = refreshTokenRepository.findByKey(authentication.getName())
                //401 Error
                .orElseThrow(() -> new RestException(HttpStatus.UNAUTHORIZED, "???????????? ??? ??????????????????."));

        // 4. Refresh Token ??????????????? ??????
        if (!refreshToken.getValue().equals(tokenRequestDto.getRefreshToken())) {
            //401 Error
            throw new RestException(HttpStatus.UNAUTHORIZED, "????????? ?????? ????????? ???????????? ????????????.");
        }

        // 5. ????????? ?????? ??????
        TokenResponseDto tokenResponseDto = jwtUtil.generateTokenDto(authentication);

        // 6. ????????? ?????? ????????????
        RefreshToken newRefreshToken = refreshToken
                .updateValue(tokenResponseDto.getRefreshToken())
                .updateExpires(tokenResponseDto.getRefreshTokenExpiresIn());

        refreshTokenRepository.save(newRefreshToken);

        // ?????? ??????
        return tokenResponseDto;
    }
}
