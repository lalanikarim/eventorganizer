<!DOCTYPE html>
<html lang="{{ app()->getLocale() }}">
<head>
    <title>{{ config('app.name')  }}</title>
    <!-- Required meta tags -->
    <meta charset="utf-8">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">

    <!-- App CSS -->
    <link rel="stylesheet" href="{{asset("css/app.css")}}">
    <link rel="stylesheet" href="https://use.fontawesome.com/releases/v5.6.0/css/all.css" integrity="sha384-aOkxzJ5uQz7WBObEZcHvV5JvRW3TUc2rNPA7pe3AwnsUohiw1Vj2Rgx2KSOkF5+h" crossorigin="anonymous">

    <!-- App JavaScript -->
    <script src="{{asset("js/app.js")}}" defer></script>
    <!-- Main JavaScript -->
    <script src="{{asset("js/utility.js")}}"></script>
</head>
<body>
<div id="app">
@include('partials.nav')

@guest
    <div class="container">
        <div class="row">
            <div class="col-md-12">
                @include('partials.errors')
                @yield('content')
            </div>
        </div>
    </div>
@else
    <div class="container">
        <div class="row">
            <div class="col-md-3 d-print-none">
                @include('partials.menu')
            </div>
            <div class="col-md-9">
                @include('partials.errors')
                @yield('content')
            </div>
        </div>
    </div>
@endguest

@include('partials.flash')
</div>
</body>
</html>